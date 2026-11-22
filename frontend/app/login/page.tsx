import type { Metadata } from "next";
import { Suspense } from "react";
import { LoginScreen } from "@/components/auth/login/LoginScreen";
import { inter, spaceGrotesk } from "@/components/landing/fonts";

export const metadata: Metadata = {
  title: "Sign in | SwiftDrop",
  description: "Sign in to your SwiftDrop customer, merchant or staff account.",
};

export default function LoginPage() {
  return (
    <div className={`${spaceGrotesk.variable} ${inter.variable} flex flex-1 flex-col`}>
      {/* LoginScreen reads the URL (tab, redirect), which needs a Suspense boundary. */}
      <Suspense fallback={<div className="min-h-screen flex-1 bg-white" />}>
        <LoginScreen />
      </Suspense>
    </div>
  );
}
