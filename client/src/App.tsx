import { useState, useRef, useEffect } from 'react';
import { Send, Sparkles } from 'lucide-react';
import { ChatMessage, type Message, type ToolCall } from './components/ChatMessage';
import { cn } from './lib/utils';
import { motion } from 'framer-motion';

const AGENT_ENDPOINT = "http://gh-arm:8200/agent/stream";

export default function App() {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '0',
      role: 'assistant',
      content: "Hi! 👋 I'm your Math Mentor. I'll help you discover answers through questions, not just give you solutions. That's how we learn! Try asking me about any math problem - from basic arithmetic to algebra!",
    }
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [streamingMessageId, setStreamingMessageId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, streamingMessageId]);

  // Typewriter effect for streaming messages
  useEffect(() => {
    if (!streamingMessageId) return;

    const message = messages.find(m => m.id === streamingMessageId);
    if (!message || !message.fullContent) return;

    const targetContent = message.fullContent;
    const currentContent = message.content || '';

    if (currentContent.length >= targetContent.length) {
      setStreamingMessageId(null);
      return;
    }

    const timeout = setTimeout(() => {
      setMessages(prev => prev.map(msg => {
        if (msg.id !== streamingMessageId) return msg;
        const charsToAdd = Math.min(2, targetContent.length - currentContent.length);
        return {
          ...msg,
          content: targetContent.slice(0, currentContent.length + charsToAdd)
        };
      }));
    }, 15); // Balanced typewriter speed

    return () => clearTimeout(timeout);
  }, [messages, streamingMessageId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      content: input
    };

    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const response = await fetch(AGENT_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          input: { messages: [{ type: "human", content: userMessage.content }] }
        }),
      });

      if (!response.ok) throw new Error('Failed to send message');

      const reader = response.body?.getReader();
      if (!reader) throw new Error('No reader available');

      const assistantMessageId = (Date.now() + 1).toString();

      // Initialize empty assistant message with thinking state
      setMessages(prev => [...prev, {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        fullContent: '',
        toolCalls: []
      }]);

      setStreamingMessageId(assistantMessageId);

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        const chunk = decoder.decode(value, { stream: true });
        buffer += chunk;
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Keep incomplete line in buffer

        for (const line of lines) {
          if (line.startsWith('event: ')) {
            // We might handle specific events here
          } else if (line.startsWith('data: ')) {
            try {
              const data = JSON.parse(line.slice(6));

              if (data && typeof data === 'object') {
                const msgs = data.agent?.messages || data.messages || (data.data && data.data.messages);
                if (msgs && Array.isArray(msgs)) {
                  const lastMsg = msgs[msgs.length - 1];

                  setMessages(prev => prev.map(msg => {
                    if (msg.id !== assistantMessageId) return msg;

                    if (lastMsg.type === 'ai' || lastMsg.role === 'assistant') {
                      const newContent = lastMsg.content || '';
                      const toolCalls = lastMsg.tool_calls || [];

                      const formattedTools: ToolCall[] = toolCalls.map((tc: any) => ({
                        id: tc.id || 'unknown',
                        name: tc.name,
                        args: tc.args,
                        status: 'running'
                      }));

                      return {
                        ...msg,
                        fullContent: typeof newContent === 'string' ? newContent : '',
                        toolCalls: formattedTools.length > 0 ? formattedTools : msg.toolCalls
                      };
                    }
                    return msg;
                  }));

                  if (lastMsg.type === 'tool' || lastMsg.role === 'tool') {
                    setMessages(prev => prev.map(msg => {
                      if (msg.id !== assistantMessageId) return msg;

                      const updatedTools = msg.toolCalls?.map(tc => {
                        if (tc.id === lastMsg.tool_call_id) {
                          return { ...tc, status: 'complete' as const, result: lastMsg.content };
                        }
                        return tc;
                      });

                      return { ...msg, toolCalls: updatedTools };
                    }));
                  }
                }
              }
            } catch (e) {
              console.warn("Error parsing stream chunk", e);
            }
          }
        }
      }

    } catch (error) {
      console.error('Error:', error);
      setStreamingMessageId(null);
      setMessages(prev => [...prev, {
        id: Date.now().toString(),
        role: 'assistant',
        content: "Error connecting to agent."
      }]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex h-screen w-full overflow-hidden font-sans">
      {/* Sidebar - Enhanced */}
      <div className="hidden md:flex flex-col w-72 glass border-r border-white/5 p-6 gap-6 relative overflow-hidden">
        {/* Background blobs for sidebar */}
        <div className="absolute top-0 left-0 w-full h-40 bg-purple-500/10 blur-3xl rounded-full -translate-y-1/2" />

        <div className="flex items-center gap-3 z-10">
          <div className="h-10 w-10 rounded-xl bg-gradient-to-tr from-emerald-500 to-teal-600 flex items-center justify-center shadow-lg shadow-emerald-500/20">
            <span className="text-white font-bold text-lg">π</span>
          </div>
          <div>
            <h1 className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-white to-emerald-200">
              Math Mentor
            </h1>
            <p className="text-xs text-emerald-300/60 font-medium tracking-wide">SOCRATIC TUTOR</p>
          </div>
        </div>

        <div className="flex-1 space-y-1 z-10">
          <p className="text-xs font-semibold text-zinc-500 mb-2 px-2 uppercase tracking-widest">Math Tools</p>
          {['Calculate', 'Solve Equations', 'Simplify Expressions', 'Topic Hints'].map((tool) => (
            <div key={tool} className="group flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-white/5 text-sm text-zinc-400 hover:text-white transition-all cursor-default">
              <div className="w-1.5 h-1.5 rounded-full bg-emerald-500/50 group-hover:bg-emerald-400 shadow-[0_0_8px_rgba(16,185,129,0.3)] transition-colors" />
              {tool}
            </div>
          ))}
        </div>

        <div className="z-10 bg-gradient-to-br from-white/5 to-transparent rounded-xl p-4 border border-white/5 backdrop-blur-md">
          <div className="text-xs font-mono text-zinc-500 mb-2"> SYSTEM STATUS</div>
          <div className="flex items-center gap-2 text-xs text-emerald-400">
            <span className="relative flex h-2 w-2">
              <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
            </span>
            Operational
          </div>
        </div>
      </div>

      {/* Main Chat Area */}
      <div className="flex-1 flex flex-col relative">
        <div className="absolute inset-0 z-0 bg-[radial-gradient(circle_at_50%_120%,rgba(120,119,198,0.1),rgba(255,255,255,0))]" />

        {/* Messages */}
        <div className="flex-1 overflow-y-auto p-4 md:p-8 pt-10 scroll-smooth z-10">
          {messages.map((message) => (
            <ChatMessage key={message.id} message={message} />
          ))}
          <div ref={messagesEndRef} className="h-4" />
        </div>

        {/* Input Area */}
        <div className="p-4 md:p-8 max-w-4xl w-full mx-auto z-20">
          <form onSubmit={handleSubmit} className="relative group">
            <div className="absolute -inset-0.5 bg-gradient-to-r from-emerald-500 via-teal-500 to-cyan-500 rounded-2xl opacity-20 group-hover:opacity-40 blur transition duration-500" />
            <div className="relative flex items-center gap-2 bg-zinc-950/80 backdrop-blur-xl p-2 rounded-2xl border border-white/10 ring-1 ring-white/5 shadow-2xl">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Ask me about your math problem..."
                className="flex-1 bg-transparent border-none outline-none px-4 py-3 text-zinc-200 placeholder-zinc-500/80 min-w-0 font-medium"
                disabled={isLoading}
              />
              <motion.button
                type="submit"
                disabled={isLoading || !input.trim()}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="p-3 rounded-xl bg-gradient-to-br from-emerald-500 to-teal-600 text-white shadow-lg shadow-emerald-500/25 disabled:opacity-50 disabled:shadow-none hover:brightness-110 transition-all"
              >
                <Send className="w-5 h-5" />
              </motion.button>
            </div>

            <div className="text-center mt-3 text-xs text-zinc-500/60 font-medium tracking-wide">
              K-12 MATH TUTOR • SOCRATIC METHOD • POWERED BY LANGGRAPH
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
