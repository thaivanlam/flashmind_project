import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import { User, LoginRequest, RegisterRequest } from '@/types/auth.types';
import { authApi } from '@/api/auth.api';
import {
  setTokens,
  clearTokens,
  setStoredUser,
  getStoredUser,
} from '@/utils/tokenStorage';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}

const initialState: AuthState = {
  user: getStoredUser<User>(),
  isAuthenticated: !!getStoredUser<User>(),
  loading: false,
  error: null,
};

export const login = createAsyncThunk(
  'auth/login',
  async (req: LoginRequest, { rejectWithValue }) => {
    try {
      const res = await authApi.login(req);
      setTokens(res.accessToken, res.refreshToken);
      setStoredUser(res.user);
      return res.user;
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message ?? 'Đăng nhập thất bại');
    }
  }
);

export const register = createAsyncThunk(
  'auth/register',
  async (req: RegisterRequest, { rejectWithValue }) => {
    try {
      const res = await authApi.register(req);
      setTokens(res.accessToken, res.refreshToken);
      setStoredUser(res.user);
      return res.user;
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } };
      return rejectWithValue(err.response?.data?.message ?? 'Đăng ký thất bại');
    }
  }
);

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    logout: (state) => {
      clearTokens();
      state.user = null;
      state.isAuthenticated = false;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(login.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(login.fulfilled, (state, action: PayloadAction<User>) => {
        state.loading = false;
        state.user = action.payload;
        state.isAuthenticated = true;
      })
      .addCase(login.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      .addCase(register.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(register.fulfilled, (state, action: PayloadAction<User>) => {
        state.loading = false;
        state.user = action.payload;
        state.isAuthenticated = true;
      })
      .addCase(register.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  },
});

export const { logout } = authSlice.actions;
export default authSlice.reducer;
