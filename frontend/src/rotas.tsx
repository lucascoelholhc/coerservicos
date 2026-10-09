import { Route, Routes } from 'react-router';

import { Moldura } from './layout/Moldura';
import { Inicio } from './paginas/inicio/Inicio';
import { NaoEncontrada } from './paginas/nao-encontrada/NaoEncontrada';

/** Todas as rotas do front. Rota que não existe cai em "Página não encontrada". */
export function Rotas() {
  return (
    <Routes>
      <Route element={<Moldura />}>
        <Route index element={<Inicio />} />
        <Route path="*" element={<NaoEncontrada />} />
      </Route>
    </Routes>
  );
}
