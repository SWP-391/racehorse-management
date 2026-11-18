import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { ArrowLeft, Lock, LockOpen, Megaphone, Send } from 'lucide-react';
import { api } from '../../api/api';
import type { ThreadDetail } from '../../api/types';
import { Avatar } from '../ChatAvatar';
import { fieldsOf, messageOf } from '../ErrorText';
import { Button } from '../ui/button';
import { DataView } from '../ui/data-view';
import { Callout } from '../ui/states';
import { useLoad } from '../../hooks/useLoad';
import { vi } from '../../i18n/vi';
import { cn } from '@/lib/utils';
import { clubDate, clubDateTime, clubTime } from '../../lib/utils';
import { notify } from '../../lib/notify';
import { INBOX_CHANGED } from './events';

const POLL_MS = 5_000;
export const TEXT_MAX = 2000;

/** One chat: every message with this person in time order, feedback to the Club Manager highlighted. */
export function ChatPane({ threadKey, onBack }: { threadKey: string; onBack: () => void }) {
  const t = vi.inbox;
  const chat = useLoad(() => api.thread(threadKey), [threadKey]);
  const { reload } = chat;
  const [text, setText] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const scroller = useRef<HTMLDivElement>(null);
  const box = useRef<HTMLTextAreaElement>(null);
  const count = chat.state.data?.messages.length ?? 0;

  useEffect(() => {
    setText('');
    setError(null);
    setFieldError(null);
  }, [threadKey]);

  // Polls only while the tab is visible (TECH-244).
  useEffect(() => {
    const timer = setInterval(() => {
      if (document.visibilityState !== 'hidden') void reload();
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [reload]);

  // Opening or answering moves the read mark: the menu and the list ask for the counts again.
  useEffect(() => {
    if (chat.state.status === 'ready') window.dispatchEvent(new Event(INBOX_CHANGED));
  }, [chat.state]);

  // A new message scrolls the thread (not the page) to the latest one.
  useLayoutEffect(() => {
    const el = scroller.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [count, threadKey, chat.state.status]);

  // The box grows with what is typed, up to about six lines, then scrolls.
  useLayoutEffect(() => {
    const el = box.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 144)}px`;
  }, [text, chat.state.status]);

  async function send(e?: FormEvent) {
    e?.preventDefault();
    if (pending) return;
    setError(null);
    setFieldError(null);
    if (!text.trim()) {
      setFieldError(t.required);
      return;
    }
    setPending(true);
    try {
      await api.sendToThread(threadKey, text);
      setText('');
      await reload();
      box.current?.focus();
    } catch (err) {
      setFieldError(fieldsOf(err).text ?? null);
      setError(fieldsOf(err).text ? null : messageOf(err));
    } finally {
      setPending(false);
    }
  }

  async function toggle(id: number, open: boolean) {
    try {
      await (open ? api.reopenConversation(id) : api.closeConversation(id));
      await reload();
    } catch (err) {
      notify.error(err);
    }
  }

  // Enter sends, Shift+Enter breaks the line; a key that only confirms a Vietnamese composition (Telex, VNI) never sends.
  function onKey(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      void send();
    }
  }

  return (
    <DataView load={chat}>
      {(c: ThreadDetail) => {
        const last = c.topics[c.topics.length - 1];
        return (
          <section aria-label={c.name} className="absolute inset-0 flex min-h-0 flex-col">
            <header className="flex items-center gap-3 border-b bg-card px-3 py-2.5 sm:px-4">
              <Button variant="ghost" size="icon" className="lg:hidden" aria-label={t.back} onClick={onBack}>
                <ArrowLeft aria-hidden />
              </Button>
              <Avatar name={c.name} className="max-sm:hidden" />
              <div className="min-w-0 flex-1">
                <h2 className="truncate text-base leading-tight font-semibold">{c.name}</h2>
                <p className="truncate text-meta text-muted-foreground">
                  {c.role === 'CLUB_MANAGER' ? t.clubRole : (vi.roles as Record<string, string>)[c.role]}
                </p>
              </div>
              {c.openTopicId ? (
                <Button variant="outline" size="sm" className="shrink-0 max-sm:px-2.5" onClick={() => void toggle(c.openTopicId!, false)}>
                  <Lock aria-hidden />
                  <span className="max-sm:sr-only">{t.close}</span>
                </Button>
              ) : (
                last && (
                  <Button variant="outline" size="sm" className="shrink-0 max-sm:px-2.5" onClick={() => void toggle(last.id, true)}>
                    <LockOpen aria-hidden />
                    <span className="max-sm:sr-only">{t.reopen}</span>
                  </Button>
                )
              )}
            </header>

            <div ref={scroller} className="min-h-0 flex-1 overflow-y-auto overscroll-contain bg-secondary/40 px-3 py-4 sm:px-6">
              <ol aria-label={t.title} className="m-0 grid list-none gap-0.5 p-0">
                {c.messages.map((m, i) => {
                  const before = c.messages[i - 1];
                  const after = c.messages[i + 1];
                  const day = clubDate(m.sentAt);
                  const newDay = !before || clubDate(before.sentAt) !== day;
                  const first = newDay || before.authorId !== m.authorId || before.feedback;
                  const end = !after || clubDate(after.sentAt) !== day || after.authorId !== m.authorId || m.feedback;
                  return (
                    <li key={m.id} className={cn('grid', first && !newDay && 'mt-2.5')}>
                      {newDay && (
                        <p className="my-3 text-center text-meta text-muted-foreground">
                          <span className="rounded-full bg-card px-3 py-1 tabular-nums">{day}</span>
                        </p>
                      )}
                      <div className={cn('flex items-end gap-2', m.mine ? 'justify-end' : 'justify-start')}>
                        {!m.mine && (end ? <Avatar name={m.authorName} size="sm" /> : <span className="w-7 shrink-0" aria-hidden />)}
                        <div className={cn('flex min-w-0 max-w-[80%] flex-col', m.mine ? 'items-end' : 'items-start', m.feedback && 'max-w-[88%]')}>
                          {!m.mine && first && <span className="mb-0.5 ml-3 text-meta text-muted-foreground">{m.authorName}</span>}
                          {m.feedback ? (
                            <div
                              title={clubDateTime(m.sentAt)}
                              className="grid max-w-full gap-1 rounded-2xl border-2 border-amber-300 bg-amber-50 px-4 py-3 text-amber-950 shadow-sm"
                            >
                              <p className="flex items-center gap-1.5 text-xs font-semibold tracking-wide text-amber-800 uppercase">
                                <Megaphone aria-hidden className="size-3.5" />
                                {t.feedbackLabel}
                              </p>
                              <p className="text-sm leading-snug [overflow-wrap:anywhere] whitespace-pre-wrap">
                                <span className="sr-only">{m.mine ? t.you : m.authorName}: </span>
                                {m.text}
                              </p>
                            </div>
                          ) : (
                            <p
                              title={clubDateTime(m.sentAt)}
                              className={cn(
                                'max-w-full rounded-2xl px-3.5 py-2 text-sm leading-snug [overflow-wrap:anywhere] whitespace-pre-wrap',
                                m.mine ? 'bg-primary text-primary-foreground' : 'border bg-card text-foreground',
                                m.mine ? (first ? '' : 'rounded-tr-md') + (end ? '' : ' rounded-br-md') : (first ? '' : 'rounded-tl-md') + (end ? '' : ' rounded-bl-md'),
                              )}
                            >
                              <span className="sr-only">{m.mine ? t.you : m.authorName}: </span>
                              {m.text}
                            </p>
                          )}
                          {end && <span className="mx-3 mt-0.5 text-[0.6875rem] text-muted-foreground tabular-nums">{clubTime(m.sentAt)}</span>}
                        </div>
                      </div>
                    </li>
                  );
                })}
              </ol>
              {!c.openTopicId && last?.stateAt && last.stateBy && (
                <p className="my-4 text-center text-meta text-muted-foreground">{t.closedBy(last.stateBy, clubDateTime(last.stateAt))}</p>
              )}
            </div>

            <form className="border-t bg-card px-3 py-3 sm:px-4" noValidate onSubmit={(e) => void send(e)}>
              {error && (
                <Callout tone="danger" role="alert" className="mb-2">
                  {error}
                </Callout>
              )}
              {!c.openTopicId ? (
                <Callout tone="info">{t.closedNotice}</Callout>
              ) : (
                <>
                  <div className="flex items-end gap-2">
                    <textarea
                      ref={box}
                      rows={1}
                      aria-label={t.answerLabel}
                      aria-invalid={fieldError ? true : undefined}
                      aria-describedby={fieldError ? 'chat-error' : undefined}
                      maxLength={TEXT_MAX}
                      placeholder={t.answerPlaceholder}
                      value={text}
                      onKeyDown={onKey}
                      onChange={(e) => {
                        setText(e.target.value);
                        if (fieldError) setFieldError(null);
                      }}
                      className="max-h-36 min-h-10 flex-1 resize-none rounded-2xl border border-input bg-secondary/60 px-4 py-2 text-sm leading-snug placeholder:text-muted-foreground focus-visible:border-ring focus-visible:outline-2 focus-visible:outline-ring/40 aria-[invalid=true]:border-destructive"
                    />
                    <Button type="submit" size="icon" className="size-10 shrink-0 rounded-full" loading={pending} aria-label={t.send}>
                      <Send aria-hidden />
                    </Button>
                  </div>
                  <div className="mt-1 flex justify-between gap-3 px-1 text-meta text-muted-foreground">
                    <span id="chat-error" role={fieldError ? 'alert' : undefined} className="text-destructive">
                      {fieldError}
                    </span>
                    <span className="tabular-nums">{t.counter(text.length, TEXT_MAX)}</span>
                  </div>
                </>
              )}
            </form>
          </section>
        );
      }}
    </DataView>
  );
}
