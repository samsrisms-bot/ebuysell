import { beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app';
import { prisma } from '../src/lib/prisma';
import { resetDb, validEventPayload } from './testUtils';

const app = createApp();
const API_KEY = process.env.API_KEY as string;

describe('POST /api/ivr/events', () => {
  beforeEach(resetDb);

  it('rejects requests with no API key', async () => {
    const res = await request(app).post('/api/ivr/events').send(validEventPayload());
    expect(res.status).toBe(401);
  });

  it('rejects requests with the wrong API key', async () => {
    const res = await request(app)
      .post('/api/ivr/events')
      .set('Authorization', 'Bearer wrong-key')
      .send(validEventPayload());
    expect(res.status).toBe(401);
  });

  it('accepts the API key via X-Api-Key as well as Authorization: Bearer', async () => {
    const res = await request(app)
      .post('/api/ivr/events')
      .set('X-Api-Key', API_KEY)
      .send(validEventPayload());
    expect(res.status).toBe(200);
  });

  it('rejects a malformed body instead of crashing', async () => {
    const res = await request(app)
      .post('/api/ivr/events')
      .set('Authorization', `Bearer ${API_KEY}`)
      .send({ event: 'not_a_real_event', number: '123' });
    expect(res.status).toBe(400);
    expect(res.body.error).toBeDefined();
  });

  it('stores a valid call_result event', async () => {
    const res = await request(app)
      .post('/api/ivr/events')
      .set('Authorization', `Bearer ${API_KEY}`)
      .send(validEventPayload());

    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
    expect(res.body.wasDuplicate).toBe(false);

    const events = await prisma.callEvent.findMany();
    expect(events).toHaveLength(1);
    expect(events[0].number).toBe('+919000000001');
  });

  it('dedupes a retried event with the same number/campaignId/startedAt instead of creating a second row', async () => {
    const payload = validEventPayload();
    await request(app).post('/api/ivr/events').set('Authorization', `Bearer ${API_KEY}`).send(payload);
    const second = await request(app).post('/api/ivr/events').set('Authorization', `Bearer ${API_KEY}`).send(payload);

    expect(second.status).toBe(200);
    expect(second.body.wasDuplicate).toBe(true);

    const events = await prisma.callEvent.findMany();
    expect(events).toHaveLength(1);
  });

  it('treats different startedAt values as distinct events even for the same number', async () => {
    await request(app).post('/api/ivr/events').set('Authorization', `Bearer ${API_KEY}`).send(validEventPayload());
    await request(app)
      .post('/api/ivr/events')
      .set('Authorization', `Bearer ${API_KEY}`)
      .send(validEventPayload({ startedAt: 1732600100000, endedAt: 1732600118000 }));

    const events = await prisma.callEvent.findMany();
    expect(events).toHaveLength(2);
  });

  it('auto-creates a Lead when event is callback_request', async () => {
    const res = await request(app)
      .post('/api/ivr/events')
      .set('Authorization', `Bearer ${API_KEY}`)
      .send(validEventPayload({ event: 'callback_request', digits: ['3'] }));

    expect(res.body.leadCreated).toBe(true);
    const leads = await prisma.lead.findMany();
    expect(leads).toHaveLength(1);
    expect(leads[0].number).toBe('+919000000001');
    expect(leads[0].name).toBe('Asha');
    expect(leads[0].status).toBe('NEW');
  });

  it('does not create a second Lead when a callback_request is retried (dedup)', async () => {
    const payload = validEventPayload({ event: 'callback_request', digits: ['3'] });
    await request(app).post('/api/ivr/events').set('Authorization', `Bearer ${API_KEY}`).send(payload);
    await request(app).post('/api/ivr/events').set('Authorization', `Bearer ${API_KEY}`).send(payload);

    const leads = await prisma.lead.findMany();
    expect(leads).toHaveLength(1);
  });

  it('does not create a Lead for a plain call_result event', async () => {
    await request(app).post('/api/ivr/events').set('Authorization', `Bearer ${API_KEY}`).send(validEventPayload());
    const leads = await prisma.lead.findMany();
    expect(leads).toHaveLength(0);
  });

  it('adds the number to the opt-out list and marks its contact OPTED_OUT when digit 9 is pressed on a campaign call', async () => {
    const campaign = await prisma.campaign.create({ data: { name: 'Test campaign' } });
    const contact = await prisma.contact.create({
      data: { campaignId: campaign.id, name: 'Asha', phone: '+919000000001', status: 'PENDING' }
    });

    await request(app)
      .post('/api/ivr/events')
      .set('Authorization', `Bearer ${API_KEY}`)
      .send(validEventPayload({ campaignId: campaign.id, digits: ['9'] }));

    const optOut = await prisma.optOut.findUnique({ where: { phone: '+919000000001' } });
    expect(optOut).not.toBeNull();

    const updatedContact = await prisma.contact.findUnique({ where: { id: contact.id } });
    expect(updatedContact?.status).toBe('OPTED_OUT');
  });

  it('does not opt out a number that presses 9 outside a campaign (no campaignId)', async () => {
    await request(app)
      .post('/api/ivr/events')
      .set('Authorization', `Bearer ${API_KEY}`)
      .send(validEventPayload({ campaignId: null, digits: ['9'] }));

    const optOut = await prisma.optOut.findUnique({ where: { phone: '+919000000001' } });
    expect(optOut).toBeNull();
  });
});

describe('GET /api/ivr/campaigns/:id/contacts', () => {
  beforeEach(resetDb);

  it('requires the API key', async () => {
    const campaign = await prisma.campaign.create({ data: { name: 'C' } });
    const res = await request(app).get(`/api/ivr/campaigns/${campaign.id}/contacts`);
    expect(res.status).toBe(401);
  });

  it('returns 404 for an unknown campaign', async () => {
    const res = await request(app)
      .get('/api/ivr/campaigns/does-not-exist/contacts')
      .set('Authorization', `Bearer ${API_KEY}`);
    expect(res.status).toBe(404);
  });

  it('returns contacts in {name, phone, vars} shape', async () => {
    const campaign = await prisma.campaign.create({ data: { name: 'C' } });
    await prisma.contact.create({
      data: { campaignId: campaign.id, name: 'Asha Rao', phone: '+919000000001', vars: { city: 'Hyderabad' } }
    });

    const res = await request(app)
      .get(`/api/ivr/campaigns/${campaign.id}/contacts`)
      .set('Authorization', `Bearer ${API_KEY}`);

    expect(res.status).toBe(200);
    expect(res.body).toEqual([{ name: 'Asha Rao', phone: '+919000000001', vars: { city: 'Hyderabad' } }]);
  });

  it('excludes a contact whose number is on the opt-out list', async () => {
    const campaign = await prisma.campaign.create({ data: { name: 'C' } });
    await prisma.contact.create({ data: { campaignId: campaign.id, name: 'Opted out', phone: '+919000000009' } });
    await prisma.contact.create({ data: { campaignId: campaign.id, name: 'Still dialable', phone: '+919000000001' } });
    await prisma.optOut.create({ data: { phone: '+919000000009' } });

    const res = await request(app)
      .get(`/api/ivr/campaigns/${campaign.id}/contacts`)
      .set('Authorization', `Bearer ${API_KEY}`);

    expect(res.body).toHaveLength(1);
    expect(res.body[0].phone).toBe('+919000000001');
  });
});
