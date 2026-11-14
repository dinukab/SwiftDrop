import { api } from "@/lib/api";

export interface AssistantReply {
  reply: string;
}

/** Send a chat message to the AI delivery assistant (POST /api/v1/assistant/chat). */
export function sendAssistantMessage(message: string, token: string): Promise<AssistantReply> {
  return api.post<AssistantReply>("/api/v1/assistant/chat", { message }, { token });
}
