import { redirect } from "next/navigation";

// The portal has no overview page; its home is the first tab. Sign-in still lands on /customer.
export default function CustomerPortalPage() {
  redirect("/customer/new");
}
