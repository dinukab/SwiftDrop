import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

export function middleware(request: NextRequest) {
 
  const userRole = request.cookies.get('userRole')?.value;
  const token = request.cookies.get('token')?.value;
  const { pathname } = request.nextUrl;

 
  if (!token && (pathname.startsWith('/admin') || pathname.startsWith('/courier') || pathname.startsWith('/merchant'))) {
    return NextResponse.redirect(new URL('/login', request.url));
  }

  // 1. Admin/Dispatcher Routes protection
  if (pathname.startsWith('/admin') && userRole !== 'DISPATCHER_ADMIN') {
    return NextResponse.redirect(new URL('/unauthorized', request.url));
  }

  // 2. Merchant Routes protection
  if (pathname.startsWith('/merchant') && userRole !== 'MERCHANT') {
    return NextResponse.redirect(new URL('/unauthorized', request.url));
  }

  // 3. Courier Routes protection
  if (pathname.startsWith('/courier') && userRole !== 'COURIER') {
    return NextResponse.redirect(new URL('/unauthorized', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/admin/:path*',
    '/merchant/:path*',
    '/courier/:path*',
  ],
};