import { Component, inject } from '@angular/core';
import { ThemeService } from '../../core/services/theme.service';
import { RouterLink } from '@angular/router';
import { UI_COMPONENTS } from '../../shared/ui';

@Component({
  selector: 'app-landing', standalone: true, imports: [RouterLink, ...UI_COMPONENTS],
  templateUrl: './landing.component.html', styleUrl: './landing.component.css'
})
export class LandingComponent { readonly theme = inject(ThemeService); }
