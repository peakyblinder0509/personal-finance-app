-- A budget's "spent" figure is now computed on demand from the transactions
-- table (sum of EXPENSE amounts in the same category, month and year), so the
-- stored column is gone. Nothing ever updated it, so it was always 0 and would
-- have drifted from the real transactions anyway. Computing it gives one source
-- of truth.
ALTER TABLE budgets DROP COLUMN spent_amount;
