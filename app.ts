import type { Answer, Result, Sources } from './app.contract.d.ts';

export const grants = '';

const sources: Sources = {
  status: (): Result<'status'> => ({ message: 'Koma is running.' }),
};
export const answer: Answer = (source, args, store, storage, native) =>
  sources[source](args, store, storage, native);
