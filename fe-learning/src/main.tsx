import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import App from '@/app/App';
import '@/index.css';

const container: HTMLElement | null = document.getElementById('root');

if (container === null) {
  throw new Error('Không tìm thấy phần tử gốc #root trong index.html.');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);