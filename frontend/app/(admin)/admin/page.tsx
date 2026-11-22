import { redirect } from "next/navigation";

// The portal has no overview page; its home is the first tab. Sign-in still lands on /admin.
export default function AdminPortalPage() {
  redirect("/admin/users");
}
