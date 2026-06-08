export enum UserRole {
  ADMIN = 'ADMIN',
  SUPERVISEUR = 'SUPERVISEUR',
  ATC = 'ATC',
  PISTE = 'PISTE',
  AIS = 'AIS',
  RH_FORMATION = 'RH_FORMATION',
  QUALITE = 'QUALITE',
  CONSULTATION = 'CONSULTATION'
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface JwtResponse {
  token: string;
  type: string;
  id: number;
  email: string;
  fullName: string;
  roles: UserRole[];
}

export interface User {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phoneNumber?: string;
  badgeNumber?: string;
  matricule?: string;      // UML: matricule agent
  grade?: string;          // UML: grade agent
  serviceNom?: string;     // UML: service de rattachement
  active: boolean;
  roles: UserRole[];
  createdAt: string;
  avatar?: string;
  lastLogin?: string | Date;
  department?: string;
  location?: string;
  certifications?: string[];
  trainingLevel?: string;
  passwordChangedAt?: string | Date;
  twoFactorEnabled?: boolean;
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber?: string;
  badgeNumber?: string;
  matricule?: string;
  grade?: string;
  serviceNom?: string;
  roles: UserRole[];
  department?: string;
  location?: string;
  active?: boolean;
}

export interface UpdateUserRequest {
  firstName?: string;
  lastName?: string;
  email?: string;
  phoneNumber?: string;
  badgeNumber?: string;
  matricule?: string;
  grade?: string;
  serviceNom?: string;
  roles?: UserRole[];
  active?: boolean;
  department?: string;
  location?: string;
  certifications?: string[];
  trainingLevel?: string;
}

export interface UserStatistics {
  total: number;
  active: number;
  inactive: number;
  byRole: Record<UserRole, number>;
  byDepartment: Record<string, number>;
}

export interface RolePermission {
  role: UserRole;
  permissions: string[];
  description: string;
}
