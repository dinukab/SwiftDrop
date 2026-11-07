import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
  const userRole = request.cookies.get('userRole')?.value;
  const { pathname } = request.nextUrl;

  // Admin routes protection
  if (pathname.startsWith('/admin') && userRole !== 'DISPATCHER_ADMIN') {
    return NextResponse.redirect(new URL('/unauthorized', request.url));
  }

  // Courier routes protection
  if (pathname.startsWith('/courier') && userRole !== 'COURIER') {
    return NextResponse.redirect(new URL('/unauthorized', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: ['/admin/:path*', '/courier/:path*'],
};