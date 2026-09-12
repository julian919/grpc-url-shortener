/**
 * user-service: the user PROFILE (name, status). Credentials live in auth-service and are never
 * mirrored here.
 *
 * <h2>Layout</h2>
 *
 * <ul>
 *   <li>{@code UserGrpcService}, {@code UserExceptionAdvice} -- the transport edge. Not features;
 *       thin adapters that delegate. They may use anything.
 *   <li>{@code registration/}, {@code profile/} -- the features. Each owns its use case.
 *   <li>{@code shared/} -- types used by more than one feature. See its package-info.
 *   <li>{@code config/} -- wiring only, no logic.
 * </ul>
 *
 * <p>Where does a new class go? Count the FEATURES that use it: one -> inside that feature; two or
 * more -> {@code shared/}; none, because it is wiring -> {@code config/}.
 */
package com.example.user;
