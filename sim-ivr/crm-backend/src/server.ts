import 'dotenv/config';
import { createApp } from './app';
import { logger } from './lib/logger';

const port = Number(process.env.PORT) || 8080;

const app = createApp();

app.listen(port, () => {
  logger.info(`SIM IVR CRM backend listening on port ${port}`);
  logger.info('Point the app\'s Settings > CRM base URL + API key at this server.');
});
