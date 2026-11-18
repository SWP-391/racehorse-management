import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { MessagesSquare } from 'lucide-react';
import { api } from '../api/api';
import { ChatPane } from '../components/inbox/ChatPane';
import { ThreadList } from '../components/inbox/ThreadList';
import { NewMessageDialog } from '../components/NewMessageDialog';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import { cn } from '@/lib/utils';

/**
 * The inbox as a chat app (BR-227, BR-231, BR-233): the chats on the left, one per person, the open chat on the right
 * (one at a time on a phone). `/inbox/<number>` is a conversation id from an old link or a notice: it opens the chat it is in.
 */
export default function InboxPage() {
  const t = vi.inbox;
  const navigate = useNavigate();
  const key = useParams().key ?? null;
  const { me } = useAuth();
  const [composing, setComposing] = useState<null | 'message' | 'feedback'>(null);

  useEffect(() => {
    if (!key || !/^\d+$/.test(key)) return;
    let live = true;
    api
      .conversation(Number(key))
      .then((d) => live && navigate(`/inbox/${encodeURIComponent(d.threadKey)}`, { replace: true }))
      .catch(() => live && navigate('/inbox', { replace: true }));
    return () => {
      live = false;
    };
  }, [key, navigate]);

  const chatKey = key && !/^\d+$/.test(key) ? key : null;

  return (
    <>
      <div className="-mb-12 grid grid-rows-[minmax(0,1fr)] h-[calc(100dvh-6rem)] min-h-[28rem] overflow-hidden rounded-xl border bg-card shadow-sm md:h-[calc(100dvh-6.5rem)] lg:h-[calc(100dvh-7rem)] lg:grid-cols-[22rem_minmax(0,1fr)]">
        <div className={cn('min-h-0 border-r', key ? 'hidden lg:block' : 'block')}>
          <ThreadList activeKey={chatKey} onOpen={(k) => navigate(`/inbox/${encodeURIComponent(k)}`)} onNew={() => setComposing('message')}
            onFeedback={me?.role === 'CLUB_MANAGER' ? undefined : () => setComposing('feedback')}
          />
        </div>
        <div className={cn('relative min-h-0 min-w-0', key ? 'block' : 'hidden lg:block')}>
          {chatKey ? (
            <ChatPane key={chatKey} threadKey={chatKey} onBack={() => navigate('/inbox')} />
          ) : (
            <div className="grid h-full place-items-center bg-secondary/40 p-8 text-center">
              <div className="grid justify-items-center gap-2 text-muted-foreground">
                <MessagesSquare aria-hidden className="size-10" />
                <p className="font-medium text-foreground">{t.pickChat}</p>
                <p className="max-w-xs text-sm">{t.pickChatText}</p>
              </div>
            </div>
          )}
        </div>
      </div>
      <NewMessageDialog
        open={composing !== null}
        mode={composing ?? 'message'}
        onClose={() => setComposing(null)}
        onStarted={(id) => navigate(`/inbox/${id}`)}
      />
    </>
  );
}
