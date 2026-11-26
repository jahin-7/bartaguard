import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";

export const metadata: Metadata = {
  title: "Privacy policy · BartaGuard",
  description: "BartaGuard checks messages on your device. Nothing you check is uploaded.",
};

// One policy for the Android app, the iPhone app and this website. Plain language first, because
// store reviewers and users both read it. Keep it in step with what the code actually does.

const UPDATED = "26 November 2026";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="glass rounded-[22px] p-5">
      <h2 className="text-lg font-semibold">{title}</h2>
      <div className="mt-2 grid gap-2 text-[15px] leading-relaxed text-sub">{children}</div>
    </section>
  );
}

export default function Privacy() {
  return (
    <main className="mx-auto grid max-w-2xl gap-4 px-4 py-8">
      <Link href="/" className="flex items-center gap-2.5 font-bold"><Image src="/logo.svg" alt="" width={32} height={32} unoptimized className="size-8 rounded-[9px]" />BartaGuard</Link>
      <div className="px-1">
        <h1 className="text-[34px] font-bold leading-tight tracking-tight">Privacy policy</h1>
        <p className="text-sub">গোপনীয়তা নীতি · Last updated {UPDATED}</p>
      </div>

      <Section title="The short version · সংক্ষেপে">
        <p>Every check happens on your own phone or in your own browser. BartaGuard has no account, no ads and no analytics, and it never sends the messages you check to us or to anyone else.</p>
        <p>প্রতিটি যাচাই আপনার নিজের ফোনে বা ব্রাউজারেই হয়। BartaGuard-এ কোনো অ্যাকাউন্ট, বিজ্ঞাপন বা অ্যানালিটিক্স নেই, আর আপনি যে মেসেজ যাচাই করেন তা কখনো আমাদের বা অন্য কারো কাছে পাঠানো হয় না।</p>
      </Section>

      <Section title="Android app">
        <p><b>No internet access.</b> The Android app does not request the internet permission, so it cannot send anything off your phone.</p>
        <p><b>Notification access (optional).</b> If you turn on auto-check and grant notification access, the app reads the text of new notifications from messaging apps (SMS, WhatsApp, Messenger, Telegram, imo, Viber). It checks that text on the phone and shows a warning if it looks like a scam. Notifications from other apps are ignored. It does not read your SMS inbox, contacts or call log.</p>
        <p><b>Notifications.</b> Used only to show scam warnings.</p>
        <p><b>Battery setting.</b> On some phones we ask you to set battery use to &ldquo;Unrestricted&rdquo; so the system doesn&rsquo;t stop the checker. This gives the app no access to your data.</p>
        <p><b>History (off by default).</b> If you turn it on, the app keeps your last 50 checks on the phone: the verdict, the time, where it came from and the first 90 characters of the message. You can clear it in Settings, and turning history off deletes it.</p>
      </Section>

      <Section title="iPhone app">
        <p><b>SMS filter (optional).</b> If you choose BartaGuard under Settings → Messages → Unknown &amp; Spam, iOS shows it SMS from numbers that are not in your contacts. The filter decides on the phone whether a message goes to Junk; it makes no network requests. iOS never gives it iMessages or messages from saved contacts.</p>
        <p><b>Share sheet.</b> When you share a message to BartaGuard, it is checked on the phone and not saved unless history is on.</p>
        <p><b>History (off by default)</b> works as on Android, and stays on the phone.</p>
      </Section>

      <Section title="Website">
        <p>The checker runs in your browser. Text you type or paste is not uploaded.</p>
        <p><b>Screenshots.</b> Reading text from a screenshot also happens in your browser. The first time you use it, your browser downloads the text-recognition files from a public CDN (jsDelivr). The CDN sees an ordinary download request, like for any web page; your screenshot is never sent.</p>
        <p>We store your language and theme choice in your browser&rsquo;s local storage. There are no cookies and no tracking.</p>
      </Section>

      <Section title="What we collect">
        <p>Nothing. We don&rsquo;t run a server that receives your messages, and we don&rsquo;t collect names, phone numbers, contacts, location or device identifiers. Because we hold no data about you, there is nothing for us to share, sell or delete. Uninstalling the app removes everything it stored.</p>
      </Section>

      <Section title="Accuracy">
        <p>BartaGuard gives a second opinion, not a guarantee. It can miss scams and can flag genuine messages. Never send money, PINs or one-time codes because of a message, whatever BartaGuard says.</p>
      </Section>

      <Section title="Children">
        <p>BartaGuard is for general audiences and collects no personal information from anyone, including children.</p>
      </Section>

      <Section title="Changes and contact">
        <p>If this policy changes, we will update the date at the top of this page.</p>
        <p>Questions: <a href="mailto:vistasverbose@gmail.com" className="font-semibold text-accent">vistasverbose@gmail.com</a></p>
      </Section>
    </main>
  );
}
