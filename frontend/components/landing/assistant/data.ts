/**
 * Copy for the landing-page chat widget. The backend assistant (POST /api/v1/assistant/chat)
 * answers signed-in customers only, so signed-out visitors are invited to sign in first.
 */
export const ASSISTANT_COPY = {
  launcherOpen: "Open the SwiftDrop assistant",
  launcherClose: "Close the SwiftDrop assistant",
  title: "SwiftDrop assistant",
  subtitle: "Parcel status and delivery fees",
  greeting: "Hi! I'm the SwiftDrop assistant. Ask me where your parcel is or how much a delivery costs.",
  signedOutText:
    "Sign in with your customer account to chat. I can look up your parcel by its tracking number and estimate delivery fees.",
  signIn: "Sign in",
  createAccount: "Create an account",
  notCustomerText: "The assistant is available for customer accounts. You're signed in with a different account type.",
  inputLabel: "Message the assistant",
  placeholder: "Ask about a parcel or a delivery fee…",
  send: "Send",
  thinking: "Thinking…",
  forbidden: "The assistant is available for customer accounts only.",
  errorPrefix: "Sorry, something went wrong:",
  /** Each question is sent on its own (the backend keeps no conversation history). */
  footnote: "Answers come from Google Gemini using live SwiftDrop data. Each question is answered on its own.",
};

export const SUGGESTIONS = ["Where is my parcel?", "How much to send 2 kg to Kandy?", "What are the delivery zones?"];

/** Backend limit (ChatRequest: @Size(max = 1000)). */
export const MAX_MESSAGE_LENGTH = 1000;

/** Query flag that opens the widget, used as the sign-in return address. */
export const OPEN_CHAT_PARAM = "chat";
export const SIGN_IN_HREF = `/login?redirect=${encodeURIComponent(`/?${OPEN_CHAT_PARAM}=open`)}`;
