import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ShieldOff } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { vi } from '../i18n/vi';
import HorseForm from '../components/HorseForm';
import { PageHeader } from '../components/ui/display';
import { EmptyState } from '../components/ui/states';

/** Register or edit a horse (Club Manager) on its own page; the form itself is shared with the horse's edit popup. */
export default function HorseFormPage() {
  const { id } = useParams();
  const editing = id !== undefined;
  const navigate = useNavigate();
  const { can } = useAuth();
  const [name, setName] = useState('');

  if (!can('MANAGE_HORSES')) {
    return (
      <div role="alert">
        <EmptyState icon={ShieldOff} title={vi.common.accessDenied} className="mt-8" />
      </div>
    );
  }

  const title = editing ? vi.horses.edit : vi.horses.register;
  const crumbs = [
    { label: vi.horses.title, to: '/horses' },
    ...(editing && name ? [{ label: name, to: `/horses/${id}` }] : []),
    { label: title },
  ];

  return (
    <>
      <PageHeader title={title} description={vi.horses.editSubtitle} crumbs={crumbs} />
      <HorseForm
        horseId={editing ? Number(id) : undefined}
        onName={setName}
        onSaved={(saved, photoFailed) => navigate(photoFailed ? `/horses/${saved.id}/edit` : `/horses/${saved.id}`)}
        onCancel={() => navigate(editing ? `/horses/${id}` : '/horses')}
      />
    </>
  );
}
