import { Button, type ButtonProps } from '@mantine/core'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useOnline } from './useOnline'

// A link to an editing screen; offline it is a disabled button, since nothing there can be saved.
export default function OnlineLinkButton({ to, ...props }: ButtonProps & { to: string; children: ReactNode }) {
  return useOnline() ? <Button component={Link} to={to} {...props} /> : <Button disabled {...props} />
}
