import { Container, Paper, Title } from '@mantine/core'
import type { ReactNode } from 'react'

export default function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Container size={420} my="xl">
      <Title order={1} ta="center" mb="md">{title}</Title>
      <Paper withBorder p="lg" radius="md">{children}</Paper>
    </Container>
  )
}
