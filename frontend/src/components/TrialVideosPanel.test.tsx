import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import TrialVideosPanel from './TrialVideosPanel';
import TrainingSection from './TrainingSection';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { TrialVideo } from '../api/types';
import { pageOf } from '../test/page';

vitest.mock('../api/api', () => ({
  api: {
    videos: vitest.fn(),
    uploadVideo: vitest.fn(),
    deleteVideo: vitest.fn(),
    schedule: vitest.fn(),
    plansOf: vitest.fn(),
  },
}));

const video = (id: number): TrialVideo => ({
  id, sessionId: 7, horseId: 9, fileName: `chay-thu-${id}.mp4`, sizeBytes: 150 * 1024 * 1024, uploadedBy: 'HLV A',
  uploadedAt: '2026-10-21T03:00:00Z', contentUrl: `/api/videos/${id}/content`,
});

const mp4 = () => new File([new Uint8Array([0, 0, 0, 24, 102, 116, 121, 112])], 'chay-thu.mp4', { type: 'video/mp4' });

describe('trial-run videos (tasks 3.4 to 3.7)', () => {
  test('AC-147 the Head Trainer uploads a file and the new video is listed with its size', async () => {
    vitest.mocked(api.videos).mockResolvedValueOnce([]).mockResolvedValueOnce([video(1)]);
    vitest.mocked(api.uploadVideo).mockResolvedValue(video(1));
    renderAs('HEAD_TRAINER', <TrialVideosPanel sessionId={7} completed />);

    await userEvent.upload(await screen.findByLabelText(/^Tệp video/), mp4());
    await userEvent.click(screen.getByRole('button', { name: 'Tải video lên' }));

    expect(api.uploadVideo).toHaveBeenCalledWith(7, expect.any(File));
    expect(await screen.findByText(/chay-thu-1\.mp4, 150\.0 MB/)).toBeInTheDocument();
    expect(screen.getByLabelText('chay-thu-1.mp4').tagName).toBe('VIDEO');
    expect(screen.getByLabelText('chay-thu-1.mp4')).toHaveAttribute('src', '/api/videos/1/content');
  });

  test('AC-150 the limit refusal from the service is shown', async () => {
    vitest.mocked(api.videos).mockResolvedValue([video(1), video(2)]);
    vitest.mocked(api.uploadVideo).mockRejectedValue(
      new ApiError(409, 'VIDEO_LIMIT_REACHED', 'Mỗi buổi tập có tối đa 3 video.', { file: 'Mỗi buổi tập có tối đa 3 video.' }),
    );
    renderAs('HEAD_TRAINER', <TrialVideosPanel sessionId={7} completed />);

    await userEvent.upload(await screen.findByLabelText(/^Tệp video/), mp4());
    await userEvent.click(screen.getByRole('button', { name: 'Tải video lên' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Mỗi buổi tập có tối đa 3 video.');
  });

  test('AC-150 with three videos the upload form is replaced by the limit notice', async () => {
    vitest.mocked(api.videos).mockResolvedValue([video(1), video(2), video(3)]);
    renderAs('HEAD_TRAINER', <TrialVideosPanel sessionId={7} completed />);
    expect(await screen.findByText('Buổi tập đã có đủ 3 video.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tải video lên' })).not.toBeInTheDocument();
  });

  test('AC-159 an Owner can play but sees no delete or upload control', async () => {
    vitest.mocked(api.videos).mockResolvedValue([video(1)]);
    renderAs('HORSE_OWNER', <TrialVideosPanel sessionId={7} completed />);
    expect(await screen.findByLabelText('chay-thu-1.mp4')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Xóa video' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Tải video lên' })).not.toBeInTheDocument();
  });

  test('AC-166 the Owner opens a completed trial run without video and reads "Chưa có video"', async () => {
    vitest.mocked(api.schedule).mockResolvedValue(pageOf([
      { id: 7, date: '2026-10-20', startTime: '07:00:00', type: 'TRIAL_RUN', state: 'COMPLETED', form: 4, comment: 'Tốt', videoCount: 0, plannedEnd: null, plannedEndNextDay: false, endedAt: null },
      { id: 8, date: '2026-10-19', startTime: null, type: 'GALLOP', state: 'COMPLETED', form: 3, comment: null, videoCount: 0, plannedEnd: null, plannedEndNextDay: false, endedAt: null },
    ]));
    vitest.mocked(api.videos).mockResolvedValue([]);
    renderAs('HORSE_OWNER', <TrainingSection horseId={9} archived={false} />);

    const buttons = await screen.findAllByRole('button', { name: /Xem video/ });
    expect(buttons).toHaveLength(1);
    await userEvent.click(buttons[0]);
    expect(await screen.findByText('Chưa có video.')).toBeInTheDocument();
    expect(api.videos).toHaveBeenCalledWith(7);
  });

  test('the Head Trainer is told videos come only after the trial run is Hoàn thành', async () => {
    vitest.mocked(api.videos).mockResolvedValue([]);
    renderAs('HEAD_TRAINER', <TrialVideosPanel sessionId={7} completed={false} />);
    expect(await screen.findByText('Chỉ tải video lên khi buổi chạy thử đã Hoàn thành.')).toBeInTheDocument();
  });
});

describe('deleting a video (verifier finding: the replacement for window.confirm)', () => {
  test('the Head Trainer is asked first: Cancel keeps the video, confirming deletes it', async () => {
    vitest.mocked(api.videos).mockResolvedValue([video(1)]);
    vitest.mocked(api.deleteVideo).mockResolvedValue(undefined as never);
    renderAs('HEAD_TRAINER', <TrialVideosPanel sessionId={7} completed />);

    await userEvent.click(await screen.findByRole('button', { name: 'Xóa video' }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Xóa video này?' });
    expect(dialog).toHaveTextContent('Thao tác được ghi vào nhật ký.');
    expect(api.deleteVideo).not.toHaveBeenCalled();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Hủy' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(api.deleteVideo).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Xóa video' }));
    await userEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Xóa video' }));
    expect(api.deleteVideo).toHaveBeenCalledWith(1);
  });
});

