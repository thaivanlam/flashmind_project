import { FC, ReactNode } from 'react';
import Navbar from './Navbar';

interface Props {
  children: ReactNode;
}

const Layout: FC<Props> = ({ children }) => {
  return (
    <div className="min-h-screen">
      <Navbar />
      <main className="max-w-6xl mx-auto px-4 py-6">{children}</main>
    </div>
  );
};

export default Layout;
