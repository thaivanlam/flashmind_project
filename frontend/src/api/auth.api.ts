import axiosClient from './axiosClient';
import {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
} from '@/types/auth.types';

export const authApi = {
  login: async (req: LoginRequest): Promise<AuthResponse> => {
    const { data } = await axiosClient.post<AuthResponse>('/auth/login', req);
    return data;
  },

  register: async (req: RegisterRequest): Promise<AuthResponse> => {
    const { data } = await axiosClient.post<AuthResponse>('/auth/register', req);
    return data;
  },
};
