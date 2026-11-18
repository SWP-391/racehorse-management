import { act, fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi as vitest } from 'vitest';
import InboxPage from './InboxPage';
import { subjectOf } from '../components/NewMessageDialog';
import { renderAs } from '../test/render';
import { api } from '../api/api';
import { ApiError } from '../api/client';
import type { RecipientOptions, ThreadDetail, ThreadRow } from '../api/types';

vitest.mock('../api/api', () => ({
  api: {
    threads: vitest.fn(),
    thread: vitest.fn(),
    sendToThread: vitest.fn(),
    conversation: vitest.fn(),
    closeConversation: vitest.fn(),
    reopenConversation: vitest.fn(),
    inboxRecipients: vitest.fn(),
    startConversation: vitest.fn(),
  },
}));
const navigate = vitest.fn();
vitest.mock('react-router-dom', async (orig) => ({ ...(await orig<typeof import('react-router-dom')>()), useNavigate: () => navigate }));

const page = (items: ThreadRow[], total = items.length) => ({ items, page: 0, size: 20, total });
const row = (over: Partial<ThreadRow> = {}): ThreadRow => ({
  key: 'club',
  name: 'Quản lý CLB',
  role: 'CLUB_MANAGER',
  preview: 'Cho ăn sớm hơn 30 phút được không?',
  lastAt: '2026-11-17T03:00:00Z',
  unread: 0,
  topics: 1,
  openTopics: 1,
  hasFeedback: false,
  ...over,
});
const msg = (id: number, conversationId: number, over: Record<string, unknown> = {}) => ({
  id,
  conversationId,
  authorId: 1,
  authorName: 'Bà Lý Thị Hoa',
  text: `tin ${id}`,
  sentAt: '2026-11-17T03:00:00Z',
  mine: true,
  feedback: false,
  ...over,
});
const chat = (over: Partial<ThreadDetail> = {}): ThreadDetail => ({
  key: 'club',
  name: 'Quản lý CLB',
  role: 'CLUB_MANAGER',
  topics: [{ id: 5, subject: 'Xin đổi giờ cho ăn', horseName: null, kind: 'MESSAGE', state: 'OPEN', stateBy: null, stateAt: null }],
  messages: [msg(1, 5, { text: 'Sớm hơn 30 phút được không?' }), msg(2, 5, { mine: false, authorId: 9, authorName: 'Quản lý CLB', text: 'Đã ghi nhận' })],
  openTopicId: 5,
  ...over,
});
const club = { accountId: null, name: 'Quản lý CLB', role: 'CLUB_MANAGER', clubManagers: true } as const;
const trainer = { accountId: 7, name: 'Trần Văn Hùng', role: 'HEAD_TRAINER', clubManagers: false } as const;
const noHorse: RecipientOptions = { horses: [{ id: 3, name: 'Hồng Phong' }], recipients: [club] };
const withHorse: RecipientOptions = { horses: [{ id: 3, name: 'Hồng Phong' }], recipients: [club, trainer] };

const show = (at = '/inbox') => renderAs('HORSE_OWNER', <InboxPage />, '/inbox/:key?', at);

beforeEach(() => {
  navigate.mockReset();
  vitest.mocked(api.threads).mockReset().mockResolvedValue(page([]));
  vitest.mocked(api.thread).mockReset().mockResolvedValue(chat());
  vitest.mocked(api.sendToThread).mockReset().mockResolvedValue({ id: 3 });
  vitest.mocked(api.conversation).mockReset();
  vitest.mocked(api.closeConversation).mockReset().mockResolvedValue(undefined);
  vitest.mocked(api.reopenConversation).mockReset().mockResolvedValue(undefined);
  vitest.mocked(api.inboxRecipients).mockReset().mockImplementation(async (horseId) => (horseId ? withHorse : noHorse));
  vitest.mocked(api.startConversation).mockReset().mockResolvedValue({ id: 11 });
});

describe('chat list: one chat per person (BR-231, BR-233)', () => {
  test('an empty inbox says so and offers to write', async () => {
    show();
    expect(await screen.findByText('Chưa có hội thoại nào')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Gửi tin nhắn/ }).length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { level: 1, name: 'Hộp thư' })).toBeInTheDocument();
  });

  test('AC-502 an unread chat shows its count and opening it goes to the chat of that person', async () => {
    vitest.mocked(api.threads).mockResolvedValue(page([row({ unread: 2, topics: 3, hasFeedback: true })]));
    show();
    expect(await screen.findByText('2')).toBeInTheDocument();
    expect(screen.getByLabelText('Phản hồi tới Quản lý CLB')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Quản lý CLB/ }));
    expect(navigate).toHaveBeenCalledWith('/inbox/club');
  });

  test('the list is paged: a second page is asked for with the next button', async () => {
    vitest.mocked(api.threads).mockResolvedValue(page([row()], 45));
    show();
    await screen.findByText('Trang 1/3');
    await userEvent.click(screen.getByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(api.threads).toHaveBeenLastCalledWith({ page: 1, size: 20, q: '' }));
    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeEnabled();
  });

  test('the search asks the service after a pause and says when nothing matches', async () => {
    show();
    await screen.findByText('Chưa có hội thoại nào');
    await userEvent.type(screen.getByRole('searchbox'), 'hoa');
    await waitFor(() => expect(api.threads).toHaveBeenLastCalledWith({ page: 0, size: 20, q: 'hoa' }));
    expect(await screen.findByText('Không có cuộc trò chuyện nào khớp.')).toBeInTheDocument();
  });

  test('an old link with a conversation id opens the chat that conversation is in', async () => {
    vitest.mocked(api.conversation).mockResolvedValue({ threadKey: 'u9' } as never);
    show('/inbox/42');
    await waitFor(() => expect(api.conversation).toHaveBeenCalledWith(42));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/inbox/u9', { replace: true }));
  });
});

describe('chat (BR-227, BR-232, BR-233)', () => {
  test('messages of every conversation show in one chat in time order, with no topic headings', async () => {
    vitest.mocked(api.thread).mockResolvedValue(
      chat({
        topics: [
          { id: 5, subject: 'Xin đổi giờ cho ăn', horseName: null, kind: 'MESSAGE', state: 'CLOSED', stateBy: 'Quản lý CLB', stateAt: '2026-11-17T04:00:00Z' },
          { id: 6, subject: 'Hóa đơn tháng này', horseName: 'Hồng Phong', kind: 'MESSAGE', state: 'OPEN', stateBy: null, stateAt: null },
        ],
        messages: [msg(1, 5), msg(2, 6, { text: 'Có vẻ sai' })],
        openTopicId: 6,
      }),
    );
    show('/inbox/club');
    expect(await screen.findByRole('heading', { level: 2, name: 'Quản lý CLB' })).toBeInTheDocument();
    const items = screen.getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('tin 1');
    expect(items[1]).toHaveTextContent('Có vẻ sai');
    expect(screen.queryByText(/Chủ đề/)).toBeNull();
    expect(screen.queryByText('Hóa đơn tháng này')).toBeNull();
    expect(screen.queryByRole('button', { name: /Chủ đề mới/ })).toBeNull();
  });

  test('a feedback is highlighted and its first message only', async () => {
    vitest.mocked(api.thread).mockResolvedValue(
      chat({
        topics: [{ id: 5, subject: 'Cảm ơn đội chăm sóc', horseName: null, kind: 'FEEDBACK', state: 'OPEN', stateBy: null, stateAt: null }],
        messages: [msg(1, 5, { text: 'Ngựa khỏe hơn nhiều', feedback: true }), msg(2, 5, { mine: false, authorName: 'Quản lý CLB', text: 'Cảm ơn anh' })],
      }),
    );
    show('/inbox/club');
    const label = await screen.findByText('Phản hồi tới Quản lý CLB');
    expect(label.closest('div')).toHaveClass('border-amber-300');
    expect(screen.getByText('Ngựa khỏe hơn nhiều')).toBeInTheDocument();
    expect(screen.getAllByText('Phản hồi tới Quản lý CLB')).toHaveLength(1);
  });

  test('a message is plain text, never markup', async () => {
    vitest.mocked(api.thread).mockResolvedValue(chat({ messages: [msg(1, 5, { mine: false, text: '<b>đậm</b> <img src=x onerror=alert(1)>' })] }));
    const { container } = show('/inbox/club');
    expect(await screen.findByText('<b>đậm</b> <img src=x onerror=alert(1)>')).toBeInTheDocument();
    expect(container.querySelector('li b')).toBeNull();
    expect(container.querySelector('li img')).toBeNull();
  });

  test('Enter sends into the chat, Shift+Enter breaks the line, an empty one is refused', async () => {
    show('/inbox/club');
    const box = await screen.findByLabelText('Trả lời');
    await userEvent.type(box, '{Enter}');
    expect(await screen.findByText('Bắt buộc nhập.')).toBeInTheDocument();
    await userEvent.type(box, 'một{Shift>}{Enter}{/Shift}hai');
    expect(box).toHaveValue('một\nhai');
    expect(api.sendToThread).not.toHaveBeenCalled();
    await userEvent.type(box, '{Enter}');
    await waitFor(() => expect(api.sendToThread).toHaveBeenCalledWith('club', 'một\nhai'));
    await waitFor(() => expect(api.thread).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText('Trả lời')).toHaveValue('');
  });

  test('a key that confirms an input-method composition does not send', async () => {
    show('/inbox/club');
    const box = await screen.findByLabelText('Trả lời');
    await userEvent.type(box, 'xin chao');
    fireEvent.keyDown(box, { key: 'Enter', isComposing: true });
    expect(api.sendToThread).not.toHaveBeenCalled();
  });

  test('the service refusal is shown', async () => {
    vitest.mocked(api.sendToThread).mockRejectedValue(new ApiError(409, 'CONVERSATION_CLOSED', 'Hội thoại đã đóng; hãy mở lại để nhắn tiếp.', {}));
    show('/inbox/club');
    await userEvent.type(await screen.findByLabelText('Trả lời'), 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('Hội thoại đã đóng; hãy mở lại để nhắn tiếp.')).toBeInTheDocument();
  });

  test('AC-500 when the chat is closed there is no box and it can be reopened', async () => {
    vitest.mocked(api.thread).mockResolvedValue(
      chat({
        topics: [{ id: 5, subject: 'Xin đổi giờ cho ăn', horseName: null, kind: 'MESSAGE', state: 'CLOSED', stateBy: 'Quản lý CLB', stateAt: '2026-11-17T04:00:00Z' }],
        openTopicId: null,
      }),
    );
    show('/inbox/club');
    expect(await screen.findByText(/Cuộc trò chuyện đã đóng/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Trả lời')).toBeNull();
    expect(screen.getByText(/Đã đóng bởi Quản lý CLB lúc/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Mở lại' }));
    await waitFor(() => expect(api.reopenConversation).toHaveBeenCalledWith(5));
  });

  test('the open topic can be closed', async () => {
    show('/inbox/club');
    await userEvent.click(await screen.findByRole('button', { name: 'Đóng hội thoại' }));
    await waitFor(() => expect(api.closeConversation).toHaveBeenCalledWith(5));
  });

  test('a chat that is not found shows the service message and no crash', async () => {
    vitest.mocked(api.thread).mockRejectedValue(new ApiError(404, 'NOT_FOUND', 'Không tìm thấy hội thoại.', {}));
    show('/inbox/u99');
    expect(await screen.findByText(/Không tìm thấy hội thoại/)).toBeInTheDocument();
  });

  test('the answer box counts to 2000 characters', async () => {
    show('/inbox/club');
    await userEvent.type(await screen.findByLabelText('Trả lời'), 'abc');
    expect(screen.getByText('3/2000')).toBeInTheDocument();
    expect(screen.getByLabelText('Trả lời')).toHaveAttribute('maxlength', '2000');
  });
});

describe('polling every 5 seconds only while the tab is visible (TECH-244)', () => {
  afterEach(() => {
    vitest.useRealTimers();
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'visible' });
  });

  test('it reads the chat again while visible and stops while hidden', async () => {
    vitest.useFakeTimers({ shouldAdvanceTime: true });
    show('/inbox/club');
    await screen.findByRole('heading', { level: 2, name: 'Quản lý CLB' });
    expect(api.thread).toHaveBeenCalledTimes(1);
    await act(async () => {
      await vitest.advanceTimersByTimeAsync(5_100);
    });
    expect(api.thread).toHaveBeenCalledTimes(2);
    Object.defineProperty(document, 'visibilityState', { configurable: true, value: 'hidden' });
    await act(async () => {
      await vitest.advanceTimersByTimeAsync(15_000);
    });
    expect(api.thread).toHaveBeenCalledTimes(2);
  });
});

describe('new message (BR-227 to BR-229)', () => {
  async function open() {
    renderAs('HORSE_OWNER', <InboxPage />);
    await userEvent.click((await screen.findAllByRole('button', { name: /Gửi tin nhắn/ }))[0]);
    return screen.findByRole('dialog');
  }

  test('an empty form is refused in the form and nothing is sent', async () => {
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('Bắt buộc chọn người nhận.')).toBeInTheDocument();
    expect(screen.getAllByText('Bắt buộc nhập.')).toHaveLength(1);
    expect(api.startConversation).not.toHaveBeenCalled();
  });

  test('the recipients follow the horse chosen', async () => {
    await open();
    expect(screen.queryByRole('option', { name: /Trần Văn Hùng/ })).toBeNull();
    await userEvent.selectOptions(screen.getByLabelText('Ngựa'), 'Hồng Phong');
    expect(await screen.findByRole('option', { name: /Trần Văn Hùng/ })).toBeInTheDocument();
    expect(api.inboxRecipients).toHaveBeenLastCalledWith(3);
  });

  test('AC-487 a message to the Club Manager role is sent and the conversation opens', async () => {
    await open();
    await userEvent.selectOptions(await screen.findByLabelText('Gửi tới'), 'Quản lý CLB');
    await userEvent.type(screen.getByLabelText('Nội dung'), 'Sớm hơn 30 phút');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() =>
      expect(api.startConversation).toHaveBeenCalledWith({
        subject: 'Sớm hơn 30 phút',
        text: 'Sớm hơn 30 phút',
        horseId: null,
        recipientId: null,
        toClubManagers: true,
        kind: 'MESSAGE',
      }),
    );
    expect(navigate).toHaveBeenCalledWith('/inbox/11');
  });

  test('the Feedback button sends a feedback to the club with only a text, marked as feedback and with no horse', async () => {
    renderAs('HORSE_OWNER', <InboxPage />, '/inbox/:key?', '/inbox');
    await userEvent.click(await screen.findByRole('button', { name: 'Feedback' }));
    await screen.findByRole('dialog');
    expect(screen.queryByLabelText('Gửi tới')).toBeNull();
    expect(screen.queryByLabelText('Ngựa')).toBeNull();
    expect(screen.queryByLabelText('Tiêu đề')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('Bắt buộc nhập.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Nội dung phản hồi'), 'Ngựa khỏe hơn');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() =>
      expect(api.startConversation).toHaveBeenCalledWith({
        subject: 'Ngựa khỏe hơn',
        text: 'Ngựa khỏe hơn',
        horseId: null,
        recipientId: null,
        toClubManagers: true,
        kind: 'FEEDBACK',
      }),
    );
  });

  test('a Club Manager has no Feedback button (feedback goes to them)', async () => {
    renderAs('CLUB_MANAGER', <InboxPage />, '/inbox/:key?', '/inbox');
    await screen.findByText('Chưa có hội thoại nào');
    expect(screen.queryByRole('button', { name: 'Feedback' })).toBeNull();
  });

  test('the start of a long text is the title the service keeps, cut to 120 characters', () => {
    expect(subjectOf('  Dòng   một\nhai  ')).toBe('Dòng một hai');
    expect(subjectOf('a'.repeat(300))).toHaveLength(120);
  });

  test('a person with a horse is sent as a recipient id', async () => {
    await open();
    await userEvent.selectOptions(screen.getByLabelText('Ngựa'), 'Hồng Phong');
    await userEvent.selectOptions(await screen.findByLabelText('Gửi tới'), 'Trần Văn Hùng · Huấn luyện viên Trưởng');
    await userEvent.type(screen.getByLabelText('Nội dung'), 'Tuần này');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() =>
      expect(api.startConversation).toHaveBeenCalledWith({
        subject: 'Tuần này',
        text: 'Tuần này',
        horseId: 3,
        recipientId: 7,
        toClubManagers: false,
        kind: 'MESSAGE',
      }),
    );
  });

  test('the service refusal is shown next to the field it names', async () => {
    vitest.mocked(api.startConversation).mockRejectedValue(
      new ApiError(400, 'HORSE_REQUIRED', 'Bắt buộc chọn ngựa.', { horseId: 'Bắt buộc chọn ngựa.' }),
    );
    await open();
    await userEvent.selectOptions(await screen.findByLabelText('Gửi tới'), 'Quản lý CLB');
    await userEvent.type(screen.getByLabelText('Nội dung'), 'b');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi' }));
    expect(await screen.findByText('Bắt buộc chọn ngựa.')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });
});
