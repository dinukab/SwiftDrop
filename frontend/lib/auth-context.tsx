"use client";

import React, { createContext, useContext, useEffect, useState, useCallback } from "react";
import { api, onUnauthorized, type UserResponse } from "./api";

const TOKEN_STORAGE_KEY = "swiftdrop_token";

export interface AuthContextValue {
  user: UserResponse | null;
  token: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  /** Stores the token, loads GET /api/v1/users/me, and returns that profile. Throws if the profile cannot be loaded. */
  login: (token: string) => Promise<UserResponse>;
  logout: () => void;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const clearAuth = useCallback(() => {
    if (typeof window !== "undefined") {
      window.sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    }
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const unsubscribe = onUnauthorized(() => {
      clearAuth();
      if (typeof window !== "undefined") {
        const publicPaths = ["/login", "/signup", "/track", "/"];
        const currentPath = window.location.pathname;
        const isPublic = publicPaths.includes(currentPath) || currentPath.startsWith("/track");
        if (!isPublic) {
          const targetUrl = new URL(`/login?expired=true&redirect=${encodeURIComponent(currentPath)}`, window.location.origin).href;
          window.location.assign(targetUrl);
        }
      }
    });

    let isMounted = true;
    const storedToken =
      typeof window !== "undefined" ? window.sessionStorage.getItem(TOKEN_STORAGE_KEY) : null;

    if (!storedToken) {
      // Delay state update out of synchronous effect execution to prevent cascading renders
      queueMicrotask(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });
      return;
    }

    api
      .get<UserResponse>("/api/v1/users/me", { token: storedToken })
      .then((currentUser) => {
        if (isMounted) {
          setUser(currentUser);
          setToken(storedToken);
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (isMounted) {
          clearAuth();
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
      unsubscribe();
    };
  }, [clearAuth]);

  const login = useCallback(async (accessToken: string): Promise<UserResponse> => {
    if (typeof window !== "undefined") {
      window.sessionStorage.setItem(TOKEN_STORAGE_KEY, accessToken);
    }
    setIsLoading(true);
    try {
      const currentUser = await api.get<UserResponse>("/api/v1/users/me", { token: accessToken });
      setUser(currentUser);
      setToken(accessToken);
      return currentUser;
    } catch (error) {
      clearAuth();
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, [clearAuth]);

  const logout = useCallback(() => {
    clearAuth();
  }, [clearAuth]);

  const hasRole = useCallback(
    (role: string): boolean => {
      return Boolean(user?.roles?.includes(role));
    },
    [user]
  );

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isLoading,
        isAuthenticated: Boolean(user && token),
        login,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
