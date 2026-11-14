/**
 * Validates and sanitizes internal redirect paths to prevent open redirect vulnerabilities.
 * Accepts only safe relative paths starting with a single '/' (e.g. "/customer", "/track?code=123").
 * Rejects protocol-relative URLs ('//'), backslash tricks ('/\\'), scheme prefixes ('https:', 'javascript:'),
 * and control characters, falling back to the default fallback path.
 */
export function sanitizeInternalRedirect(target: string | null | undefined, fallback = "/"): string {
  if (!target || typeof target !== "string") {
    return fallback;
  }

  const trimmed = target.trim();
  if (trimmed === "" || trimmed === "/") {
    return "/";
  }

  // Must begin with a single '/' and not start with '//' or '/\'
  if (!trimmed.startsWith("/") || trimmed.startsWith("//") || trimmed.startsWith("/\\")) {
    return fallback;
  }

  // Reject CRLF or control characters
  if (/[\x00-\x1F\x7F]/.test(trimmed)) {
    return fallback;
  }

  try {
    const parsed = new URL(trimmed, "http://localhost");
    // Ensure the host authority was not parsed, origin is local, and destination is not a login loop
    if (
      parsed.origin !== "http://localhost" ||
      !parsed.pathname.startsWith("/") ||
      parsed.pathname === "/login"
    ) {
      return fallback;
    }
    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return fallback;
  }
}

/**
 * Maps backend roles from GET /api/v1/users/me to the portal home path.
 * Never trust a role submitted by the client; callers must pass roles returned by the API.
 * Priority matches the single-role accounts used today, with staff preferred if multiple exist.
 */
export function getDashboardPathForRoles(roles: string[] | null | undefined): string {
  const normalized = new Set(
    (roles ?? []).map((role) => role.replace(/^ROLE_/, "").toUpperCase())
  );

  if (normalized.has("DISPATCHER_ADMIN")) {
    return "/admin";
  }
  if (normalized.has("COURIER")) {
    return "/courier";
  }
  if (normalized.has("MERCHANT")) {
    return "/merchant";
  }
  return "/customer";
}

/**
 * Chooses the post-login destination: a sanitized explicit redirect when present,
 * otherwise the role-based dashboard from API-returned roles.
 */
export function resolvePostLoginDestination(
  redirectParam: string | null | undefined,
  roles: string[] | null | undefined
): string {
  const roleHome = getDashboardPathForRoles(roles);
  if (!redirectParam || redirectParam.trim() === "" || redirectParam.trim() === "/") {
    return roleHome;
  }
  const sanitized = sanitizeInternalRedirect(redirectParam, roleHome);
  return sanitized === "/" ? roleHome : sanitized;
}
