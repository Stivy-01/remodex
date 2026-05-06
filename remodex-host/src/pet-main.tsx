import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { PetApp } from './PetApp'

createRoot(document.getElementById('pet-root')!).render(
  <StrictMode>
    <PetApp />
  </StrictMode>,
)
