import { Suspense } from "react";
import { SiteNavbar } from "@/components/landing/site-navbar";
import { ChatWidget } from "@/components/landing/assistant/ChatWidget";
import { Hero } from "@/components/landing/hero/Hero";
import { ServicesSection } from "@/components/landing/services/ServicesSection";
import { HowItWorks } from "@/components/landing/how-it-works/HowItWorks";
import { AudienceCta } from "@/components/landing/audience-cta/AudienceCta";
import { Footer } from "@/components/landing/footer/Footer";
import { inter, spaceGrotesk } from "@/components/landing/fonts";

export default function Home() {
  return (
    <div className={`${spaceGrotesk.variable} ${inter.variable} flex flex-1 flex-col`}>
      <SiteNavbar tone="dark" />
      <main className="flex flex-1 flex-col">
        <Hero />
        <ServicesSection />
        <HowItWorks />
        <AudienceCta />
      </main>
      <Footer />
      {/* The widget reads ?chat=open from the URL, which needs a Suspense boundary. */}
      <Suspense fallback={null}>
        <ChatWidget />
      </Suspense>
    </div>
  );
}
