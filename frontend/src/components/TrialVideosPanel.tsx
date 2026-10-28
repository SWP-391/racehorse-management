import { useState, type FormEvent } from 'react';
import { CircleAlert, Film, Info, Trash2 } from 'lucide-react';
import { api } from '../api/api';
import { useAuth } from '../auth/AuthContext';
import { useLoad } from '../hooks/useLoad';
import { messageOf } from './ErrorText';
import { vi } from '../i18n/vi';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogTitle,
  AlertDialogTrigger,
} from './ui/alert-dialog';
import { Button } from './ui/button';
import { DataView } from './ui/data-view';
import { Section } from './ui/display';
import { FormField } from './ui/form-field';
import { FileInput } from './ui/file-input';
import { Callout, EmptyState, Skeleton } from './ui/states';
import { notify } from '../lib/notify';

const MAX_PER_SESSION = 3;

/**
 * Videos of one trial run (REQ-027): everyone allowed to watch sees the list and players; the Head Trainer also
 * uploads (only once the trial run is Hoàn thành) and deletes (BR-104 to BR-108). Every limit is checked again by the
 * service, whose message is shown as is.
 */
export default function TrialVideosPanel({ sessionId, completed, canWrite = true }: { sessionId: number; completed: boolean; canWrite?: boolean }) {
  const { can } = useAuth();
  const manage = can('MANAGE_TRIAL_VIDEOS') && canWrite;
  const watch = can('WATCH_TRIAL_VIDEOS');
  const load = useLoad(() => api.videos(sessionId), [sessionId], watch);
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!watch) return null;
  const videos = load.state.data;

  async function upload(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    setError(null);
    setBusy(true);
    try {
      await api.uploadVideo(sessionId, file);
      notify.success(vi.common.saved);
      setFile(null);
      (e.target as HTMLFormElement).reset();
      await load.reload();
    } catch (err) {
      setError(messageOf(err));
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: number) {
    setError(null);
    try {
      await api.deleteVideo(id);
      notify.success(vi.common.saved);
      await load.reload();
    } catch (err) {
      setError(messageOf(err));
    }
  }

  return (
    <Section title={vi.videos.title} id={`videos-${sessionId}`}>
      {error && (
        <Callout tone="danger" icon={CircleAlert} role="alert">
          {error}
        </Callout>
      )}
      <DataView
        load={load}
        skeleton={<Skeleton className="h-28" />}
        isEmpty={(list) => list.length === 0}
        empty={<EmptyState icon={Film} title={vi.videos.none} />}
      >
        {(list) => (
          <ul className="m-0 grid list-none gap-5 p-0 md:grid-cols-2">
            {list.map((v) => (
              <li key={v.id} className="grid content-start gap-2">
                <video
                  controls
                  preload="metadata"
                  src={v.contentUrl}
                  aria-label={v.fileName}
                  className="aspect-video w-full rounded-md border bg-foreground"
                />
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="min-w-0 text-sm text-muted-foreground">
                    {v.fileName}, {vi.videos.size(v.sizeBytes)}, {new Date(v.uploadedAt).toLocaleString('vi-VN')},{' '}
                    {vi.videos.uploadedBy}: {v.uploadedBy}
                  </p>
                  {manage && (
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button variant="ghost" size="sm">
                          <Trash2 aria-hidden />
                          {vi.videos.delete}
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogTitle>{vi.videos.deleteTitle}</AlertDialogTitle>
                        <AlertDialogDescription>{vi.videos.deleteNote}</AlertDialogDescription>
                        <AlertDialogFooter>
                          <AlertDialogCancel>{vi.common.cancel}</AlertDialogCancel>
                          <AlertDialogAction destructive onClick={() => void remove(v.id)}>
                            {vi.videos.delete}
                          </AlertDialogAction>
                        </AlertDialogFooter>
                      </AlertDialogContent>
                    </AlertDialog>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </DataView>
      {manage &&
        videos !== null &&
        (!completed ? (
          <Callout icon={Info}>{vi.videos.onlyCompleted}</Callout>
        ) : videos.length >= MAX_PER_SESSION ? (
          <Callout icon={Info}>{vi.videos.full}</Callout>
        ) : (
          <form onSubmit={upload} className="grid max-w-xl gap-3" aria-label={vi.videos.upload}>
            <FormField label={vi.videos.file}>
              <FileInput accept="video/mp4" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            </FormField>
            <div>
              <Button type="submit" disabled={!file} loading={busy}>
                {busy ? vi.videos.uploading : vi.videos.upload}
              </Button>
            </div>
          </form>
        ))}
    </Section>
  );
}
