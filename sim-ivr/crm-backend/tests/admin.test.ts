import { beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { createApp } from '../src/app';
import { prisma } from '../src/lib/prisma';
import { resetDb } from './testUtils';

const app = createApp();
const ADMIN_TOKEN = process.env.ADMIN_TOKEN as string;

describe('admin auth', () => {
  beforeEach(resetDb);

  it('rejects admin routes with no token', async () => {
    const res = await request(app).get('/api/campaigns');
    expect(res.status).toBe(401);
  });

  it('rejects admin routes with the wrong token', async () => {
    const res = await request(app).get('/api/campaigns').set('Authorization', 'Bearer wrong');
    expect(res.status).toBe(401);
  });

  it('POST /api/admin/login succeeds with the right token and fails with a wrong one', async () => {
    const ok = await request(app).post('/api/admin/login').set('Authorization', `Bearer ${ADMIN_TOKEN}`);
    expect(ok.status).toBe(200);

    const bad = await request(app).post('/api/admin/login').set('Authorization', 'Bearer wrong');
    expect(bad.status).toBe(401);
  });
});

describe('campaigns', () => {
  beforeEach(resetDb);

  it('creates and lists campaigns', async () => {
    const create = await request(app)
      .post('/api/campaigns')
      .set('Authorization', `Bearer ${ADMIN_TOKEN}`)
      .send({ name: 'Diwali outreach' });
    expect(create.status).toBe(201);
    expect(create.body.name).toBe('Diwali outreach');
    expect(create.body.status).toBe('DRAFT');

    const list = await request(app).get('/api/campaigns').set('Authorization', `Bearer ${ADMIN_TOKEN}`);
    expect(list.status).toBe(200);
    expect(list.body).toHaveLength(1);
  });

  it('rejects creating a campaign with an empty name', async () => {
    const res = await request(app)
      .post('/api/campaigns')
      .set('Authorization', `Bearer ${ADMIN_TOKEN}`)
      .send({ name: '' });
    expect(res.status).toBe(400);
  });

  it('returns campaign detail with contact/lead counts', async () => {
    const campaign = await prisma.campaign.create({ data: { name: 'C' } });
    await prisma.contact.createMany({
      data: [
        { campaignId: campaign.id, name: 'A', phone: '+911', status: 'PENDING' },
        { campaignId: campaign.id, name: 'B', phone: '+912', status: 'OPTED_OUT' }
      ]
    });

    const res = await request(app).get(`/api/campaigns/${campaign.id}`).set('Authorization', `Bearer ${ADMIN_TOKEN}`);
    expect(res.status).toBe(200);
    expect(res.body.contactCounts.total).toBe(2);
    expect(res.body.contactCounts.optedOut).toBe(1);
  });

  it('imports a CSV, skipping opted-out numbers and invalid rows', async () => {
    const campaign = await prisma.campaign.create({ data: { name: 'C' } });
    await prisma.optOut.create({ data: { phone: '+919000000009' } });

    const csv = [
      'name,phone,city',
      'Asha Rao,+919000000001,Hyderabad',
      'Opted Out Person,+919000000009,Delhi',
      'No Phone Person,,Chennai'
    ].join('\n');

    const res = await request(app)
      .post(`/api/campaigns/${campaign.id}/contacts/csv`)
      .set('Authorization', `Bearer ${ADMIN_TOKEN}`)
      .attach('file', Buffer.from(csv), { filename: 'contacts.csv', contentType: 'text/csv' });

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ imported: 1, skippedOptedOut: 1, skippedInvalid: 1 });

    const contacts = await prisma.contact.findMany({ where: { campaignId: campaign.id } });
    expect(contacts).toHaveLength(1);
    expect(contacts[0].phone).toBe('+919000000001');
    expect(contacts[0].vars).toEqual({ city: 'Hyderabad' });
  });

  it('returns 404 importing a CSV into an unknown campaign', async () => {
    const res = await request(app)
      .post('/api/campaigns/does-not-exist/contacts/csv')
      .set('Authorization', `Bearer ${ADMIN_TOKEN}`)
      .attach('file', Buffer.from('name,phone\nA,+911'), { filename: 'c.csv', contentType: 'text/csv' });
    expect(res.status).toBe(404);
  });
});

describe('leads', () => {
  beforeEach(resetDb);

  async function makeLead() {
    const event = await prisma.callEvent.create({
      data: {
        dedupeKey: 'k1',
        event: 'callback_request',
        direction: 'incoming',
        number: '+919000000001',
        sim: 0,
        flow: 'sample-incoming',
        digits: ['3'],
        durationMs: 1000,
        startedAt: new Date(),
        endedAt: new Date()
      }
    });
    return prisma.lead.create({ data: { number: '+919000000001', name: 'Asha', sourceCallEventId: event.id } });
  }

  it('lists leads and filters by status', async () => {
    await makeLead();
    const all = await request(app).get('/api/leads').set('Authorization', `Bearer ${ADMIN_TOKEN}`);
    expect(all.body).toHaveLength(1);

    const closed = await request(app).get('/api/leads?status=CLOSED').set('Authorization', `Bearer ${ADMIN_TOKEN}`);
    expect(closed.body).toHaveLength(0);
  });

  it('rejects an invalid status filter', async () => {
    const res = await request(app).get('/api/leads?status=NOT_REAL').set('Authorization', `Bearer ${ADMIN_TOKEN}`);
    expect(res.status).toBe(400);
  });

  it('updates a lead status', async () => {
    const lead = await makeLead();
    const res = await request(app)
      .patch(`/api/leads/${lead.id}`)
      .set('Authorization', `Bearer ${ADMIN_TOKEN}`)
      .send({ status: 'CONTACTED' });
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('CONTACTED');
  });

  it('404s updating an unknown lead', async () => {
    const res = await request(app)
      .patch('/api/leads/does-not-exist')
      .set('Authorization', `Bearer ${ADMIN_TOKEN}`)
      .send({ status: 'CONTACTED' });
    expect(res.status).toBe(404);
  });
});

describe('GET /health', () => {
  it('reports ok with no auth required', async () => {
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body.ok).toBe(true);
  });
});
