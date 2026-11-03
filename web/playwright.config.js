import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './test', testMatch: '**/*.spec.js', fullyParallel: false, workers: 1,
  timeout: 30000, reporter: 'list',
  use: {baseURL:'http://127.0.0.1:5173', viewport:{width:390,height:844}, hasTouch:true,
    launchOptions:{executablePath:process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH||undefined,args:['--no-sandbox','--use-fake-ui-for-media-stream','--use-fake-device-for-media-stream']},
    trace:'retain-on-failure'},
  webServer:{command:'npm run build && npm run dev',port:5173,reuseExistingServer:!process.env.CI}
});
