export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthenticatedUser {
  username: string;
  userId: string;
  userName: string;
  role: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: number;
  user: AuthenticatedUser;
}
