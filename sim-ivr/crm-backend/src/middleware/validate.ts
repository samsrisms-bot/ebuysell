import { NextFunction, Request, Response } from 'express';
import { ZodTypeAny } from 'zod';

/** Validates req.body against a zod schema; on failure responds 400 with a readable message instead of throwing. */
export function validateBody(schema: ZodTypeAny) {
  return (req: Request, res: Response, next: NextFunction) => {
    const result = schema.safeParse(req.body);
    if (!result.success) {
      return res.status(400).json({
        error: 'invalid request body',
        details: result.error.issues.map((i) => ({ path: i.path.join('.'), message: i.message }))
      });
    }
    req.body = result.data;
    next();
  };
}
