import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const AuthGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.getUser();
  const requiredRole = route.data['role'];

  if (user && (!requiredRole || user.role === requiredRole)) {
    return true;
  }

  return router.createUrlTree(['/login']);
};
