// Mirrors the backend DTO records. Dates: YYYY-MM-DD; times: HH:mm[:ss]; local date-times: YYYY-MM-DDTHH:mm[:ss].

export interface TripSummary {
  id: string
  name: string
  destinations: string[]
  startDate: string
  endDate: string
  coverUrl: string | null
}

export interface Activity {
  id: string
  date: string
  position: number
  title: string
  startTime: string | null
  location: string | null
  mapsUrl: string | null
  mapsLink: string | null
  notes: string | null
}

export type BookingType = 'FLIGHT' | 'ACCOMMODATION' | 'TRANSPORT'

export interface Booking {
  id: string
  type: BookingType
  title: string
  provider: string | null
  reference: string | null
  startAt: string
  endAt: string | null
  origin: string | null
  location: string | null
  notes: string | null
}

export interface DocumentMeta {
  id: string
  bookingId: string | null
  filename: string
  contentType: string
  sizeBytes: number
  createdAt: string
  fileUrl: string
}

export interface ChecklistItem {
  id: string
  text: string
  done: boolean
  position: number
}

export interface Checklist {
  id: string
  name: string
  position: number
  items: ChecklistItem[]
}

export interface TripDetail extends TripSummary {
  activities: Activity[]
  bookings: Booking[]
  documents: DocumentMeta[]
  checklists: Checklist[]
}

export interface Me {
  id: string
  email: string
  displayName: string
}

export interface AuthConfig {
  registrationOpen: boolean
}
