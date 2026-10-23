import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError, Subject } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { AnalystDashboardComponent } from './analyst-dashboard.component';
import { AnalyticsService } from '../../../core/services/analytics.service';
import { AuthService } from '../../../core/services/auth.service';
const meta={datasetId:'dataset-1',metricVersion:'v2',definitionStatus:'PROPOSED',period:{from:'2026-01-01',to:'2026-01-31',timezone:'UTC',startInclusive:'2026-01-01T00:00Z',endExclusive:'2026-02-01T00:00Z'},filters:{},sourceCutoff:'2026-02-03T00:00Z',publishedAt:'2026-02-03T01:00Z',stale:true,completeness:'INCOMPLETE',sourceCoverage:{unassigned_fill_time:1},warnings:['Incomplete evidence']};
const overview={fill_count:2,executed_orders:1,active_clients:1,submitted_orders:1,accepted_orders:1,cohort_orders:1,rejected_orders:0,pending_orders:0,unknown_outcome_orders:0,rejection_rate_percent:0,grossNotional:[{currency:'GBP',amount:1012}],fees:[{currency:'GBP',amount:2}],netCash:[{currency:'GBP',amount:-1014}]};
const group={items:[{instrumentid:1,ticker:'ABC',quantity_unit:'SHARE',executed_quantity:10,fill_count:2,active_clients:1,executed_orders:1,buy_fills:2,sell_fills:0,period:'2026-01-10',segment_code:'RETAIL'}],total:30,page:0,size:25};
describe('Priya analytics workspace',()=>{
 let api:any;
 beforeEach(()=>{
  api={read:vi.fn((kind:string)=>of({...meta,data:kind==='overview'?overview:group})),issue:vi.fn(),report:vi.fn()};
  TestBed.configureTestingModule({imports:[AnalystDashboardComponent],providers:[{provide:AnalyticsService,useValue:api},{provide:AuthService,useValue:{getUser:()=>({username:'Priya',role:'analyst'}),logout:vi.fn()}},{provide:Router,useValue:{navigate:vi.fn()}}]});
 });
 it('labels money, incomplete evidence and provenance',()=>{
  const f=TestBed.createComponent(AnalystDashboardComponent);f.detectChanges();expect(f.nativeElement.textContent).toContain('GBP');expect(f.nativeElement.textContent).toContain('1,012');expect(f.nativeElement.textContent).toContain('STALE');expect(f.nativeElement.textContent).toContain('INCOMPLETE');expect(f.nativeElement.textContent).toContain('dataset-1');
 });
 it('waits for all queries and distinguishes failure',()=>{
  const pending=new Subject<any>();api.read.mockReturnValue(pending);const f=TestBed.createComponent(AnalystDashboardComponent);f.detectChanges();expect(f.componentInstance.loading).toBe(true);pending.error({error:{message:'Reporting unavailable'}});f.detectChanges();expect(f.componentInstance.loading).toBe(false);expect(f.nativeElement.textContent).toContain('Reporting unavailable');expect(f.componentInstance.meta).toBeNull();
 });
 it('refuses mixed dataset versions',()=>{
  api.read.mockImplementation((kind:string)=>of({...meta,datasetId:kind==='activity'?'dataset-2':'dataset-1',data:kind==='overview'?overview:group}));const f=TestBed.createComponent(AnalystDashboardComponent);f.detectChanges();expect(f.componentInstance.meta).toBeNull();expect(f.componentInstance.error).toContain('dataset changed');
 });
 it('has a unit-labelled chart table and server pagination',()=>{
  const f=TestBed.createComponent(AnalystDashboardComponent);f.detectChanges();const c=f.componentInstance;f.nativeElement.querySelectorAll('nav.tabs button')[2].click();f.detectChanges();expect(f.nativeElement.querySelector('table').textContent).toContain('10 SHARE');expect(f.nativeElement.querySelector('[role="img"]')).toBeTruthy();c.page('instruments',1);expect(api.read).toHaveBeenLastCalledWith('instruments',expect.any(Object),1,'fills','desc');
 });
 it('opens frozen values without recalculation',()=>{
  const f=TestBed.createComponent(AnalystDashboardComponent);f.detectChanges();const calls=api.read.mock.calls.length;api.report.mockReturnValue(of({reportId:'saved',version:1,snapshot:{...meta,overview:{...overview,fill_count:99},activity:group,instruments:group,'client-segments':group}}));f.componentInstance.reportId='saved';f.componentInstance.openReport();f.detectChanges();expect(f.componentInstance.overview['fill_count']).toBe(99);expect(api.read.mock.calls.length).toBe(calls);expect(f.nativeElement.textContent).toContain('Issued report');
 });
 it('reuses the issue key after failure',()=>{
  const f=TestBed.createComponent(AnalystDashboardComponent);f.detectChanges();api.issue.mockReturnValue(throwError(()=>({status:503})));f.componentInstance.issue();f.componentInstance.issue();expect(api.issue.mock.calls[0][1]).toBe(api.issue.mock.calls[1][1]);
 });
});
