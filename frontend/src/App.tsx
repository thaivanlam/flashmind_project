import { FC } from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from '@/components/common/ProtectedRoute';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import DashboardPage from '@/pages/DashboardPage';
import DecksPage from '@/pages/DecksPage';
import DeckDetailPage from '@/pages/DeckDetailPage';
import ReviewPage from '@/pages/ReviewPage';
import AnalyticsPage from '@/pages/AnalyticsPage';

const App: FC = () => {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <DashboardPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/decks"
        element={
          <ProtectedRoute>
            <DecksPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/decks/:id"
        element={
          <ProtectedRoute>
            <DeckDetailPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/review"
        element={
          <ProtectedRoute>
            <ReviewPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/analytics"
        element={
          <ProtectedRoute>
            <AnalyticsPage />
          </ProtectedRoute>
        }
      />
    </Routes>
  );
};

export default App;
