import { render, screen } from '@testing-library/react';
import { HorseAvatar } from './HorseAvatar';

describe('horse avatar (BR-184)', () => {
  test('a horse without a photo shows its initial', () => {
    render(<HorseAvatar id={4} name="sấm chớp" photoVersion={null} />);
    expect(screen.getByText('S')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});
