/**
 * Cor só por variável do tokens.css: hex ou nome de cor em qualquer outro CSS quebra o lint (e o
 * PR). O único lugar com hex é src/styles/tokens.css.
 */
export default {
  extends: ['stylelint-config-standard'],
  rules: {
    'color-no-hex': true,
    'color-named': 'never',
    // CSS Modules: classes em camelCase (estilos.botaoPrincipal)
    'selector-class-pattern': null,
  },
  overrides: [
    {
      files: ['src/styles/tokens.css'],
      rules: { 'color-no-hex': null },
    },
  ],
};
