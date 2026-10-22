import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { HelpComponent } from './help.component';

describe('Help knowledge base', () => {
  it('shows four collapsed FAQ categories without extra panels or forms', () => {
    const fixture = TestBed.createComponent(HelpComponent);
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('h1')?.textContent).toBe('Help');
    expect(page.querySelector('h2')?.textContent).toBe('FAQ');
    expect(page.querySelectorAll('details')).toHaveLength(4);
    expect(page.querySelectorAll('details[open]')).toHaveLength(0);
    expect(page.querySelectorAll('form, kbd, .telemetry')).toHaveLength(0);
    expect(page.textContent).not.toContain('support ticket');
  });

  it('lets readers expand and collapse a category', () => {
    const fixture = TestBed.createComponent(HelpComponent);
    fixture.detectChanges();
    const category: HTMLDetailsElement = fixture.nativeElement.querySelector('details');
    category.querySelector('summary')!.click();
    expect(category.open).toBe(true);
    expect(category.textContent).toContain('How are orders executed?');
    category.querySelector('summary')!.click();
    expect(category.open).toBe(false);
  });
});
