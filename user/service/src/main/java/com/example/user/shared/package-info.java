/**
 * Types used by MORE THAN ONE feature in this service. "Shared" means two or more -- not all.
 *
 * <p>Today: {@code entity/} and {@code repository/}, because {@code registration} writes a user
 * row and {@code profile} reads one. Anything used by a single feature belongs inside that
 * feature instead -- which is why {@code InvalidArgumentException} lives in
 * {@code registration/exception/}, and why auth-service has no shared package at all.
 *
 * <h2>Keeping this from becoming a dumping ground</h2>
 *
 * <ul>
 *   <li><b>No business logic.</b> Data access and types, yes. Rules about what a user may do
 *       belong in the feature that owns the use case.
 *   <li><b>Prune, don't just append.</b> Periodically re-ask how many features use each type. If
 *       the answer has dropped back to one, push it down into that feature.
 *   <li><b>If this package outgrows the biggest feature, a feature is missing.</b> Contents that
 *       cluster around a noun are a feature nobody named -- promote it, and have the other
 *       features call its service instead of reaching into its data.
 * </ul>
 */
package com.example.user.shared;
