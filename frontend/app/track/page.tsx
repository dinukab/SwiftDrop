import { TrackingPage } from "./tracking-page";

interface TrackPageProps {
  searchParams: Promise<{ code?: string | string[] }>;
}

export default async function TrackPage({ searchParams }: TrackPageProps) {
  const { code } = await searchParams;
  const initialCode = Array.isArray(code) ? code[0] ?? "" : code ?? "";

  return <TrackingPage initialCode={initialCode} />;
}
