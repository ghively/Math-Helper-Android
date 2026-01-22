import { motion } from 'framer-motion';
import { Bot, User, Wrench, Search, Calculator, Clock } from 'lucide-react';
import { cn } from '../lib/utils';

export type Message = {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    fullContent?: string;  // The complete content for typewriter effect
    toolCalls?: ToolCall[];
};

export type ToolCall = {
    id: string;
    name: string;
    args: any;
    status: 'running' | 'complete';
    result?: string;
};

const ToolIcon = ({ name }: { name: string }) => {
    switch (name) {
        case 'search_web': return <Search className="w-3 h-3" />;
        case 'calculate_complexity': return <Calculator className="w-3 h-3" />;
        case 'get_current_time': return <Clock className="w-3 h-3" />;
        default: return <Wrench className="w-3 h-3" />;
    }
};

export const ChatMessage = ({ message }: { message: Message }) => {
    const isUser = message.role === 'user';

    return (
        <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            layout
            className={cn(
                "flex w-full gap-4 max-w-4xl mx-auto mb-8",
                isUser ? "justify-end" : "justify-start"
            )}
        >
            {!isUser && (
                <div className="w-10 h-10 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 flex items-center justify-center shadow-lg shadow-purple-500/20 mt-1 shrink-0">
                    <Bot className="w-6 h-6 text-white" />
                </div>
            )}

            <div className={cn(
                "flex flex-col gap-2 max-w-[85%]",
                isUser ? "items-end" : "items-start"
            )}>
                {/* Tool Calls Display */}
                {message.toolCalls && message.toolCalls.length > 0 && (
                    <div className="flex flex-col gap-2 mb-2 w-full">
                        {message.toolCalls.map((tool) => (
                            <motion.div
                                key={tool.id}
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                className="bg-zinc-900/40 border border-white/10 rounded-md overflow-hidden text-sm"
                            >
                                <div className="flex items-center gap-2 px-3 py-2 bg-white/5 border-b border-white/5">
                                    <div className="text-purple-400"><ToolIcon name={tool.name} /></div>
                                    <span className="font-medium text-zinc-300 font-mono text-xs">{tool.name}</span>
                                    <span className="ml-auto text-xs text-zinc-500">{tool.status}</span>
                                </div>
                                <div className="px-3 py-2 font-mono text-xs text-zinc-400 bg-black/20 overflow-x-auto">
                                    {JSON.stringify(tool.args)}
                                </div>
                                {tool.result && (
                                    <div className="px-3 py-2 border-t border-white/5 bg-emerald-500/5 text-emerald-200/80 text-xs font-mono">
                                        → {tool.result}
                                    </div>
                                )}
                            </motion.div>
                        ))}
                    </div>
                )}

                {/* Message Content */}
                {(message.content || isUser || message.fullContent) && (
                    <div className={cn(
                        "relative p-4 rounded-2xl shadow-xl backdrop-blur-sm",
                        isUser
                            ? "bg-gradient-to-br from-blue-600 to-indigo-600 text-white rounded-br-none"
                            : "glass-card text-zinc-100 rounded-bl-none"
                    )}>
                        <div className="leading-relaxed whitespace-pre-wrap">
                            {message.content}
                            {/* Show thinking animation while loading/typing */}
                            {!message.content && !isUser && !message.toolCalls && message.fullContent !== undefined && (
                                <div className="flex gap-1 h-6 items-center">
                                    <span className="w-2 h-2 bg-white/40 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                                    <span className="w-2 h-2 bg-white/40 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                                    <span className="w-2 h-2 bg-white/40 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {isUser && (
                <div className="w-10 h-10 rounded-full bg-zinc-800 border border-white/10 flex items-center justify-center shrink-0 mt-1">
                    <User className="w-6 h-6 text-zinc-400" />
                </div>
            )}
        </motion.div>
    );
};
