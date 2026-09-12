import { defineConfig, globalIgnores } from 'eslint/config';
import nextVitals from 'eslint-config-next/core-web-vitals';
import nextTs from 'eslint-config-next/typescript';
import prettier from 'eslint-config-prettier';

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Turns off stylistic rules that would fight Prettier. Must stay last of the presets.
  prettier,

  // The dependency rule, enforced rather than documented: shared -> features -> app, and no
  // imports between features (compose them in app/). eslint-plugin-import already ships inside
  // eslint-config-next, so this needs no extra dependency.
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      'import/no-restricted-paths': [
        'error',
        {
          zones: [
            {
              target: './src/shared',
              from: './src/features',
              message: 'shared/ is the bottom layer; it must not import a feature.',
            },
            {
              target: './src/shared',
              from: './src/app',
              message: 'shared/ must not import routing code.',
            },
            {
              target: './src/features',
              from: './src/app',
              message: 'features/ must not import routing code; app/ composes features.',
            },
            {
              target: './src/features/links',
              from: './src/features',
              except: ['./links'],
              message: 'No cross-feature imports: compose features in app/ instead.',
            },
            {
              target: './src/features/auth',
              from: './src/features',
              except: ['./auth'],
              message: 'No cross-feature imports: compose features in app/ instead.',
            },
          ],
        },
      ],
    },
  },

  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    '.next/**',
    'out/**',
    'build/**',
    'next-env.d.ts',
    // Generated from the protos; reviewed as a contract diff, not hand-edited.
    'src/shared/api/gen/**',
  ]),
]);

export default eslintConfig;
