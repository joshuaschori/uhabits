/*
 * Copyright (C) 2016 Álinson Santos Xavier <isoron@gmail.com>
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.core.models;

import androidx.annotation.*;

import org.isoron.uhabits.core.utils.*;

import java.util.*;

/**
 * The collection of {@link Streak}s that belong to a habit.
 * <p>
 * This list is populated automatically from the list of repetitions.
 */
public class StreakList
{
    protected Habit habit;

    protected ModelObservable observable = new ModelObservable();

    ArrayList<Streak> list = new ArrayList<>();

    public void setHabit(Habit habit)
    {
        this.habit = habit;
    }

    public List<Streak> getAll()
    {
        rebuild();
        return new LinkedList<>(list);
    }

    @NonNull
    public List<Streak> getBest(int limit)
    {
        List<Streak> streaks = getAll();
        Collections.sort(streaks, (s1, s2) -> s2.compareLonger(s1));
        streaks = streaks.subList(0, Math.min(streaks.size(), limit));
        Collections.sort(streaks, (s1, s2) -> s2.compareNewer(s1));
        return streaks;
    }

    @Nullable
    public Streak getNewestComputed()
    {
        Streak newest = null;

        for (Streak s : list)
            if (newest == null || s.getEnd().isNewerThan(newest.getEnd()))
                newest = s;

        return newest;

    }

    @NonNull
    public ModelObservable getObservable()
    {
        return observable;
    }

    public void recompute()
    {
        list.clear();
        observable.notifyListeners();
    }

    public synchronized void rebuild()
    {
        Timestamp today = DateUtils.getTodayWithOffset();
        Timestamp beginning = findBeginning();
        if (beginning == null || beginning.isNewerThan(today)) return;

        int checks[] = habit.getComputedEntries().getValues(beginning, today);
        List<Streak> streaks = checkmarksToStreaks(beginning, checks);

        removeNewestComputed();
        add(streaks);
    }

    /**
     * Converts a list of checkmark values to a list of streaks.
     *
     * @param beginning the timestamp corresponding to the first checkmark
     *                  value.
     * @param checks    the checkmarks values, ordered by decreasing timestamp.
     * @return the list of streaks.
     */
    @NonNull
    protected List<Streak> checkmarksToStreaks(Timestamp beginning, int[] checks)
    {
        ArrayList<Timestamp> transitions = getTransitions(beginning, checks);

        List<Streak> streaks = new LinkedList<>();
        for (int i = 0; i < transitions.size(); i += 2)
        {
            Timestamp start = transitions.get(i);
            Timestamp end = transitions.get(i + 1);
            streaks.add(new Streak(start, end));
        }

        return streaks;
    }

    /**
     * Finds the place where we should start when recomputing the streaks.
     *
     * @return
     */
    @Nullable
    protected Timestamp findBeginning()
    {
        Streak newestStreak = getNewestComputed();
        if (newestStreak != null) return newestStreak.getStart();

        List<Entry> entries = habit.getOriginalEntries().getKnown();
        if(entries.isEmpty()) return null;
        return entries.get(entries.size() - 1).getTimestamp();
    }

    /**
     * Returns the timestamps where there was a transition from performing a
     * habit to not performing a habit, and vice-versa.
     *
     * @param beginning the timestamp for the first checkmark
     * @param checks    the checkmarks, ordered by decreasing timestamp
     * @return the list of transitions
     */
    @NonNull
    protected ArrayList<Timestamp> getTransitions(Timestamp beginning, int[] checks)
    {
        ArrayList<Timestamp> list = new ArrayList<>();
        Timestamp current = beginning;
        list.add(current);

        for (int i = 1; i < checks.length; i++)
        {
            current = current.plus(1);
            int j = checks.length - i - 1;

            if ((checks[j + 1] <= 0 && checks[j] > 0)) list.add(current);
            if ((checks[j + 1] > 0 && checks[j] <= 0)) list.add(current.minus(1));
        }

        if (list.size() % 2 == 1) list.add(current);

        return list;
    }

    protected void add(@NonNull List<Streak> streaks)
    {
        list.addAll(streaks);
        Collections.sort(list, (s1, s2) -> s2.compareNewer(s1));
        observable.notifyListeners();

    }

    protected void removeNewestComputed()
    {
        Streak newest = getNewestComputed();
        if (newest != null) list.remove(newest);

    }
}
