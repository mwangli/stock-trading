// AI_GENERATE_START -
import { create } from 'zustand';

export interface AgentConversationMessage {
  role: 'user' | 'assistant';
  content: string;
  evidenceSummary?: string;
}

interface AgentConversationState {
  ownerId: string | null;
  agentOpen: boolean;
  sessionId: string | null;
  question: string;
  messages: AgentConversationMessage[];
  loading: boolean;
  activateOwner: (ownerId: string) => void;
  setAgentOpen: (ownerId: string, agentOpen: boolean) => void;
  setSessionId: (ownerId: string, sessionId: string) => void;
  setQuestion: (ownerId: string, question: string) => void;
  appendMessage: (ownerId: string, message: AgentConversationMessage) => void;
  setLoading: (ownerId: string, loading: boolean) => void;
}

const emptyConversation = {
  agentOpen: false,
  sessionId: null,
  question: '',
  messages: [],
  loading: false,
};

export const useAgentConversationStore = create<AgentConversationState>((set) => ({
  ownerId: null,
  ...emptyConversation,
  activateOwner: (ownerId) => set((state) => state.ownerId === ownerId
    ? state
    : { ownerId, ...emptyConversation }),
  setAgentOpen: (ownerId, agentOpen) => set((state) => state.ownerId === ownerId
    ? { agentOpen }
    : state),
  setSessionId: (ownerId, sessionId) => set((state) => state.ownerId === ownerId
    ? { sessionId }
    : state),
  setQuestion: (ownerId, question) => set((state) => state.ownerId === ownerId
    ? { question }
    : state),
  appendMessage: (ownerId, nextMessage) => set((state) => state.ownerId === ownerId
    ? { messages: [...state.messages, nextMessage] }
    : state),
  setLoading: (ownerId, loading) => set((state) => state.ownerId === ownerId
    ? { loading }
    : state),
}));
// AI_GENERATE_END -
