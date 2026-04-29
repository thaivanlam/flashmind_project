import axios, { AxiosInstance, InternalAxiosRequestConfig, AxiosError } from 'axios';
import {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens,
} from '@/utils/tokenStorage';

const baseURL = import.meta.env.VITE_API_URL || '/api';

const axiosClient: AxiosInstance = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
});

axiosClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  }
);

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

axiosClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetryConfig | undefined;

    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/')
    ) {
      originalRequest._retry = true;
      try {
        const refreshToken = getRefreshToken();
        if (!refreshToken) throw new Error('No refresh token');

        const { data } = await axios.post<{
          accessToken: string;
          refreshToken: string;
        }>(`${baseURL}/auth/refresh`, { refreshToken });

        setTokens(data.accessToken, data.refreshToken);
        if (originalRequest.headers) {
          originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        }
        return axiosClient(originalRequest);
      } catch {
        clearTokens();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
