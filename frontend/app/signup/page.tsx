import type { Metadata } from "next";
import { Suspense } from "react";
import { SignupScreen } from "@/components/auth/signup/SignupScreen";
import { inter, spaceGrotesk } from "@/components/landing/fonts";

export const metadata: Metadata = {
  title: "Create an account | SwiftDrop",
  description: "Create a SwiftDrop customer account or apply for a merchant account.",
};

export default function SignupPage() {
  return (
    <div className={`${spaceGrotesk.variable} ${inter.variable} flex flex-1 flex-col`}>
      {/* SignupScreen reads the URL (?type=merchant), which needs a Suspense boundary. */}
      <Suspense fallback={<div className="min-h-screen flex-1 bg-white" />}>
        <SignupScreen />
      </Suspense>
    </div>
  );
}
