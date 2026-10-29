import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { LandingComponent } from './landing.component';

describe('Trading landing page', () => {
  it('provides separate user and staff entrances', () => {
    TestBed.configureTestingModule({ imports: [LandingComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(LandingComponent);
    fixture.detectChanges();
    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('h1')?.textContent).toContain('Trader Pro');
    expect(page.querySelector('a[href="/login?type=user"]')).toBeTruthy();
    expect(page.querySelector('a[href="/login?type=staff"]')).toBeTruthy();
  });
});
