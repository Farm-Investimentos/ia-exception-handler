import { computeFingerprint } from '../fingerprint';
import { StackFrame } from '../../types';

describe('computeFingerprint', () => {
  const frame: StackFrame = {
    file: '/app/src/services/order.service.js',
    function: 'OrderService.create',
    line: 42,
    column: 10,
    isProjectCode: true,
  };

  it('produces consistent fingerprints', () => {
    const fp1 = computeFingerprint('TypeError', [frame]);
    const fp2 = computeFingerprint('TypeError', [frame]);
    expect(fp1).toBe(fp2);
  });

  it('produces different fingerprints for different errors', () => {
    const fp1 = computeFingerprint('TypeError', [frame]);
    const fp2 = computeFingerprint('RangeError', [frame]);
    expect(fp1).not.toBe(fp2);
  });

  it('produces different fingerprints for different lines', () => {
    const frame2 = { ...frame, line: 99 };
    const fp1 = computeFingerprint('TypeError', [frame]);
    const fp2 = computeFingerprint('TypeError', [frame2]);
    expect(fp1).not.toBe(fp2);
  });

  it('returns 8 hex characters', () => {
    const fp = computeFingerprint('Error', [frame]);
    expect(fp).toMatch(/^[0-9a-f]{8}$/);
  });

  it('handles empty frames gracefully', () => {
    const fp = computeFingerprint('Error', []);
    expect(fp).toBeTruthy();
  });
});
