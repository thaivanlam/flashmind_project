export const formatDate = (iso: string): string => {
  const d = new Date(iso);
  return d.toLocaleDateString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
};

export const formatRelative = (iso: string): string => {
  const date = new Date(iso);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffDays = Math.round(
    (date.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)
  );

  if (diffDays === 0) return 'Hôm nay';
  if (diffDays === 1) return 'Ngày mai';
  if (diffDays === -1) return 'Hôm qua';
  if (diffDays > 0) return `${diffDays} ngày nữa`;
  return `${Math.abs(diffDays)} ngày trước`;
};
