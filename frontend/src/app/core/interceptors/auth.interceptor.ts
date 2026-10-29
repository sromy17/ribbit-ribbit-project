import { HttpInterceptorFn } from '@angular/common/http';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  // Platform credentials belong only on our same-origin backend requests.
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const token = localStorage.getItem('accessToken');
  if (!token || request.url.startsWith('/auth/') || /^\/api\/(login|refresh|logout)$/.test(request.url)) {
    return next(request);
  }

  return next(request.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  }));
};
