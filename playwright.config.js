const { defineConfig } = require('@playwright/test');
const { existsSync } = require('node:fs');
module.exports = defineConfig({
  testDir: './tests',
  use: {
    viewport: { width: 412, height: 915 },
    launchOptions: { executablePath: process.env.CHROMIUM_PATH || (existsSync('/usr/bin/chromium') ? '/usr/bin/chromium' : undefined), args: ['--no-sandbox'] },
  },
  reporter: 'list',
});
