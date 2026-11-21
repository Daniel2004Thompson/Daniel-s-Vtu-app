import React, { useEffect, useState } from 'react';

interface UserAvatarProps {
  avatarUrl?: string;
  fullName?: string;
  sizeClass?: string;
  textClass?: string;
  className?: string;
}

export const UserAvatar: React.FC<UserAvatarProps> = ({
  avatarUrl,
  fullName,
  sizeClass = 'h-10 w-10',
  textClass = 'text-sm',
  className = '',
}) => {
  const [imgError, setImgError] = useState(false);

  useEffect(() => {
    setImgError(false);
  }, [avatarUrl]);

  const initials = (fullName || 'U')
    .split(/\s+/)
    .filter(Boolean)
    .map((p) => p[0].toUpperCase())
    .slice(0, 2)
    .join('');

  return (
    <div
      className={`flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-gradient-to-br from-[#00B074] to-[#005A3C] font-extrabold text-white ${sizeClass} ${textClass} ${className}`}
    >
      {avatarUrl && !imgError ? (
        <img
          key={avatarUrl}
          src={avatarUrl}
          alt={fullName || 'User profile'}
          onError={() => setImgError(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        initials
      )}
    </div>
  );
};
